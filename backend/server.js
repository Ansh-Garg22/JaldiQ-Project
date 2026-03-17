/**
 * JaldiQ Backend - Express Server
 *
 * Provides REST API endpoints for the JaldiQ queue management system.
 * Includes grace period management with cron-based auto-cancellation,
 * and real-time FCM push notifications for queue updates.
 *
 * Usage:
 *   npm start        - Start the server
 *   npm run dev      - Start with auto-reload (Node 18+ --watch)
 */

const express = require("express");
const cors = require("cors");
const cron = require("node-cron");
require("dotenv").config();

const { admin, db } = require("./firebaseAdmin");

const app = express();
const PORT = process.env.PORT || 3000;
const GRACE_PERIOD_MINUTES = 15;

// ─── Middleware ───────────────────────────────────────────────
app.use(cors());
app.use(express.json());

// ─── Health Check ────────────────────────────────────────────
app.get("/health", (req, res) => {
  res.json({
    status: "OK",
    service: "JaldiQ Backend",
    timestamp: new Date().toISOString(),
  });
});

// ─── Shop Endpoints ──────────────────────────────────────────
app.get("/api/shops", async (req, res) => {
  try {
    const snapshot = await db.ref("shops").once("value");
    const shops = snapshot.val();
    res.json({ success: true, data: shops || {} });
  } catch (error) {
    console.error("Error fetching shops:", error);
    res.status(500).json({ success: false, error: error.message });
  }
});

app.get("/api/shops/:shopId", async (req, res) => {
  try {
    const { shopId } = req.params;
    const snapshot = await db.ref(`shops/${shopId}`).once("value");
    const shop = snapshot.val();
    if (!shop) {
      return res.status(404).json({ success: false, error: "Shop not found" });
    }
    res.json({ success: true, data: shop });
  } catch (error) {
    console.error("Error fetching shop:", error);
    res.status(500).json({ success: false, error: error.message });
  }
});

// ─── Grace Period: Reinstate Endpoint ────────────────────────
/**
 * POST /api/shops/:shopId/reinstate/:tokenId
 *
 * Reinstates a skipped user if they return within the 15-minute grace window.
 */
app.post("/api/shops/:shopId/reinstate/:tokenId", async (req, res) => {
  try {
    const { shopId, tokenId } = req.params;

    const tokenRef = db.ref(`shops/${shopId}/queue/${tokenId}`);
    const snapshot = await tokenRef.once("value");
    const token = snapshot.val();

    if (!token) {
      return res.status(404).json({ success: false, error: "Token not found" });
    }

    if (token.status !== "GRACE_PERIOD") {
      return res.status(400).json({
        success: false,
        error: `Token is not in grace period (current status: ${token.status})`,
      });
    }

    const skippedAt = token.skippedAt || 0;
    const elapsedMinutes = (Date.now() - skippedAt) / (1000 * 60);

    if (elapsedMinutes > GRACE_PERIOD_MINUTES) {
      await tokenRef.update({ status: "CANCELLED" });
      return res.status(410).json({
        success: false,
        error: `Grace period expired (${Math.round(elapsedMinutes)} minutes elapsed). Token cancelled.`,
      });
    }

    await tokenRef.update({
      status: "WAITING",
      skippedAt: null,
    });

    console.log(
      `✅ Token ${tokenId} reinstated for shop ${shopId} (${Math.round(elapsedMinutes)} min elapsed)`
    );

    res.json({
      success: true,
      message: `Token reinstated. ${Math.round(GRACE_PERIOD_MINUTES - elapsedMinutes)} minutes were remaining.`,
    });
  } catch (error) {
    console.error("Error reinstating token:", error);
    res.status(500).json({ success: false, error: error.message });
  }
});

// ─── Grace Period: Cron Job ──────────────────────────────────
/**
 * Runs every minute. Scans all shops for GRACE_PERIOD tokens
 * and auto-cancels those whose skippedAt is older than 15 minutes.
 */
cron.schedule("* * * * *", async () => {
  try {
    const shopsSnapshot = await db.ref("shops").once("value");
    const shops = shopsSnapshot.val();

    if (!shops) return;

    const now = Date.now();
    let cancelledCount = 0;

    for (const [shopId, shop] of Object.entries(shops)) {
      if (!shop.queue) continue;

      for (const [tokenId, token] of Object.entries(shop.queue)) {
        if (token.status !== "GRACE_PERIOD") continue;

        const skippedAt = token.skippedAt || 0;
        const elapsedMinutes = (now - skippedAt) / (1000 * 60);

        if (elapsedMinutes > GRACE_PERIOD_MINUTES) {
          await db
            .ref(`shops/${shopId}/queue/${tokenId}/status`)
            .set("CANCELLED");

          cancelledCount++;
          console.log(
            `🚫 Auto-cancelled token ${tokenId} in shop ${shopId} (${Math.round(elapsedMinutes)} min since skip)`
          );
        }
      }
    }

    if (cancelledCount > 0) {
      console.log(
        `⏰ Grace period cron: ${cancelledCount} token(s) auto-cancelled`
      );
    }
  } catch (error) {
    console.error("❌ Grace period cron error:", error);
  }
});

console.log("⏰ Grace period cron job scheduled (runs every minute)");

// ─── Phase 7: Dynamic Queue Notification Listeners ───────────
/**
 * Uses child_added on /shops to dynamically detect new shops
 * and attach listeners. Each shop listener watches currentServingNumber
 * and status changes to fire FCM notifications.
 */
const activeShopListeners = new Set();

function attachShopListener(shopId) {
  if (activeShopListeners.has(shopId)) return;
  activeShopListeners.add(shopId);

  // Listen to currentServingNumber changes
  db.ref(`shops/${shopId}/currentServingNumber`).on(
    "value",
    async (servingSnap) => {
      try {
        const currentServing = servingSnap.val() || 0;
        if (currentServing === 0) return; // Shop just opened, skip

        const shopSnap = await db.ref(`shops/${shopId}`).once("value");
        const shop = shopSnap.val();
        if (!shop || !shop.queue) return;

        for (const [tokenId, token] of Object.entries(shop.queue)) {
          if (token.status !== "WAITING") continue;

          const peopleAhead = token.number - currentServing;

          if (peopleAhead === 2) {
            await sendNotification(
              token.userId,
              "Almost Your Turn! 🔔",
              `Only 2 people ahead at ${shop.name}. Get ready!`,
              "almost_turn"
            );
          } else if (peopleAhead === 0) {
            await sendNotification(
              token.userId,
              "It's Your Turn! 🎉",
              `Head to ${shop.name} now — you're being served!`,
              "your_turn"
            );
          }
        }
      } catch (error) {
        console.error(
          `❌ Notification listener error for shop ${shopId}:`,
          error.message
        );
      }
    }
  );

  // Listen to status changes — notify all WAITING users when shop closes
  db.ref(`shops/${shopId}/status`).on("value", async (statusSnap) => {
    try {
      const status = statusSnap.val();
      if (status !== "CLOSED") return;

      const shopSnap = await db.ref(`shops/${shopId}`).once("value");
      const shop = shopSnap.val();
      if (!shop || !shop.queue) return;

      for (const [tokenId, token] of Object.entries(shop.queue)) {
        if (token.status !== "WAITING" && token.status !== "CANCELLED")
          continue;

        // Only notify if just cancelled (status was changed by closeShop)
        if (token.status === "CANCELLED") {
          await sendNotification(
            token.userId,
            "Shop Closed 🔒",
            `${shop.name} has closed for the day. Your token has been cancelled.`,
            "shop_closed"
          );
        }
      }
    } catch (error) {
      console.error(
        `❌ Status listener error for shop ${shopId}:`,
        error.message
      );
    }
  });

  console.log(`👂 Listening for queue changes on shop: ${shopId}`);
}

function startNotificationListeners() {
  // Listen for existing AND new shops dynamically
  db.ref("shops").on("child_added", (snapshot) => {
    const shopId = snapshot.key;
    if (shopId) {
      attachShopListener(shopId);
    }
  });
}

/**
 * Send a push notification to a user via FCM.
 * Looks up the user's fcmToken from the users node.
 */
async function sendNotification(userId, title, body, type) {
  try {
    if (!userId) return;

    const userSnap = await db.ref(`users/${userId}/fcmToken`).once("value");
    const fcmToken = userSnap.val();

    if (!fcmToken) {
      console.log(`⚠️ No FCM token for user ${userId}, skipping notification`);
      return;
    }

    const message = {
      token: fcmToken,
      data: {
        title,
        body,
        type,
      },
      android: {
        priority: "high",
        notification: {
          title,
          body,
          channelId: "jaldiq_queue_alerts",
          sound: "default",
        },
      },
    };

    const response = await admin.messaging().send(message);
    console.log(
      `📲 Notification sent to ${userId}: "${title}" (${response})`
    );
  } catch (error) {
    // Token might be invalid/expired — log but don't crash
    if (error.code === "messaging/registration-token-not-registered") {
      console.log(`🗑️ Stale FCM token for user ${userId}, cleaning up`);
      await db.ref(`users/${userId}/fcmToken`).remove();
    } else {
      console.error(`❌ FCM send error for ${userId}:`, error.message);
    }
  }
}

// Start listeners after a short delay to ensure Firebase is ready
setTimeout(() => {
  startNotificationListeners();
  console.log("📡 Queue notification listeners active (dynamic shop detection)");
}, 2000);

// ─── Error Handling ──────────────────────────────────────────
app.use((err, req, res, next) => {
  console.error("Unhandled error:", err);
  res.status(500).json({ success: false, error: "Internal server error" });
});

// ─── Start Server ────────────────────────────────────────────
app.listen(PORT, () => {
  console.log(`🚀 JaldiQ Backend running on http://localhost:${PORT}`);
  console.log(`   Health check: http://localhost:${PORT}/health`);
  console.log(`   Grace period: ${GRACE_PERIOD_MINUTES} minutes`);
});

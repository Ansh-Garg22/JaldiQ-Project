/**
 * JaldiQ Database Seed Script
 *
 * Seeds the Firebase Realtime Database with initial test data.
 * This creates the exact schema structure specified in the JaldiQ requirements.
 *
 * Usage:
 *   node seedDatabase.js
 *   npm run seed
 *
 * WARNING: This will overwrite existing data at the seeded paths.
 */

const { db } = require("./firebaseAdmin");

const seedData = {
  shops: {
    shopId_123: {
      name: "City Barber",
      status: "OPEN",
      currentServingNumber: 12,
      lastNumberIssued: 25,
      averageServiceTimeMinutes: 15,
      queue: {
        tokenId_abc: {
          number: 13,
          userId: "user_789",
          status: "WAITING",
        },
        tokenId_xyz: {
          number: 14,
          userId: "user_456",
          status: "WAITING",
        },
      },
    },
  },
  users: {
    user_789: {
      role: "CUSTOMER",
      activeTokenId: "tokenId_abc",
    },
  },
};

async function seedDatabase() {
  try {
    console.log("🌱 Seeding Firebase Realtime Database...\n");

    // Seed shops
    await db.ref("shops").set(seedData.shops);
    console.log("✅ Shops data seeded successfully");
    console.log("   → Shop: City Barber (shopId_123)");
    console.log("   → Currently serving: #12");
    console.log("   → Last number issued: #25");
    console.log("   → Queue tokens: tokenId_abc (#13), tokenId_xyz (#14)\n");

    // Seed users
    await db.ref("users").set(seedData.users);
    console.log("✅ Users data seeded successfully");
    console.log("   → User: user_789 (CUSTOMER, active token: tokenId_abc)\n");

    // Verify by reading back
    const shopsSnapshot = await db.ref("shops").once("value");
    const usersSnapshot = await db.ref("users").once("value");

    console.log("─── Verification ───────────────────────────────");
    console.log("Shops in DB:", JSON.stringify(shopsSnapshot.val(), null, 2));
    console.log("Users in DB:", JSON.stringify(usersSnapshot.val(), null, 2));
    console.log("────────────────────────────────────────────────\n");

    console.log("🎉 Database seeding complete!");
    process.exit(0);
  } catch (error) {
    console.error("❌ Error seeding database:", error);
    process.exit(1);
  }
}

seedDatabase();

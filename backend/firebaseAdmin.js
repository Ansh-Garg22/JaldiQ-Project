/**
 * Firebase Admin SDK Initialization
 *
 * This module initializes the Firebase Admin SDK using a service account key.
 * It exports the admin instance and a reference to the Realtime Database.
 *
 * Setup:
 * 1. Go to Firebase Console → Project Settings → Service Accounts
 * 2. Click "Generate new private key" → download the JSON file
 * 3. Place the JSON file in this directory (e.g., serviceAccountKey.json)
 * 4. Set FIREBASE_SERVICE_ACCOUNT_PATH in your .env file
 */

const admin = require("firebase-admin");
const path = require("path");
require("dotenv").config();

const serviceAccountPath =
  process.env.FIREBASE_SERVICE_ACCOUNT_PATH ||
  path.join(__dirname, "serviceAccountKey.json");

let serviceAccount;
try {
  serviceAccount = require(serviceAccountPath);
} catch (error) {
  console.error(
    "❌ Firebase service account key not found at:",
    serviceAccountPath
  );
  console.error(
    "   Please download it from Firebase Console → Project Settings → Service Accounts"
  );
  process.exit(1);
}

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  databaseURL: process.env.FIREBASE_DATABASE_URL || `https://${serviceAccount.project_id}-default-rtdb.firebaseio.com`,
});

const db = admin.database();

console.log("✅ Firebase Admin SDK initialized successfully");

module.exports = { admin, db };

/**
 * One-time setup script for the official @yeex.open account.
 *
 * WHY THIS RUNS SEPARATELY FROM THE APP:
 * The Android app only has the *client* Firebase config (google-services.json),
 * which cannot grant itself `verified: true` or bypass the identifier-uniqueness
 * rule for a privileged account — and it shouldn't be able to. This script uses
 * the Firebase Admin SDK, which has full backend access, and you run it once
 * from your own machine.
 *
 * SETUP:
 *   1. Firebase Console → Project Settings → Service accounts → Generate new
 *      private key. Save the JSON as `admin/serviceAccountKey.json`
 *      (this file is gitignored — never commit it).
 *   2. cd admin && npm install
 *   3. node seedAdmin.js
 *
 * This creates:
 *   - Firebase Auth user for yeex.open@id.yeex.app / majd740143
 *   - /users/{uid} profile: identifier "yeex.open", verified, isOfficial
 *   - /identifiers/yeex.open -> uid
 *   - /admins/{uid} = true  (grants moderation rights per database.rules.json)
 */

const admin = require("firebase-admin");
const serviceAccount = require("./serviceAccountKey.json");

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  databaseURL: "https://yeex-90774-default-rtdb.firebaseio.com",
});

const IDENTIFIER = "yeex.open";
const PASSWORD = "majd740143"; // change this after first login in production
const PSEUDO_EMAIL = `${IDENTIFIER}@id.yeex.app`;

async function seed() {
  let userRecord;
  try {
    userRecord = await admin.auth().getUserByEmail(PSEUDO_EMAIL);
    console.log("Auth user already exists:", userRecord.uid);
  } catch (e) {
    userRecord = await admin.auth().createUser({
      email: PSEUDO_EMAIL,
      password: PASSWORD,
      displayName: "yeex",
    });
    console.log("Created auth user:", userRecord.uid);
  }

  const uid = userRecord.uid;
  const db = admin.database();
  const now = Date.now();

  await db.ref(`users/${uid}`).set({
    uid,
    identifier: IDENTIFIER,
    displayName: "yeex",
    bio: "الحساب الرسمي لمنصة yeex",
    profileIconUrl: "",
    verified: true,
    verifiedReason: "official",
    externalFollowerCounts: {},
    tekingCount: 0,
    tekerCount: 0,
    createdAt: now,
    language: "ar",
    isOfficial: true,
  });

  await db.ref(`identifiers/${IDENTIFIER}`).set(uid);
  await db.ref(`admins/${uid}`).set(true);

  console.log("Done. @yeex.open is live, verified, and has admin rights.");
  process.exit(0);
}

seed().catch((err) => {
  console.error(err);
  process.exit(1);
});

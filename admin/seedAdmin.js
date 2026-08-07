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
 *   - /users/{uid} profile: identifier "yeex.open", verified, isOfficial,
 *     with the brand icon at admin/assets/yeex_open_icon.png set as
 *     profileIconUrl (Base64, same encoding the app itself uses for
 *     avatars — see MediaBase64.encodeAvatar: 320x320 JPEG q82)
 *   - /identifiers/yeex.open -> uid
 *   - /admins/{uid} = true  (grants moderation rights per database.rules.json)
 */

const fs = require("fs");
const path = require("path");
const admin = require("firebase-admin");
const serviceAccount = require("./serviceAccountKey.json");

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  databaseURL: "https://yeex-90774-default-rtdb.firebaseio.com",
});

const IDENTIFIER = "yeex.open";
const PASSWORD = "majd740143"; // change this after first login in production
const PSEUDO_EMAIL = `${IDENTIFIER}@id.yeex.app`;

// Pre-baked 320x320 JPEG q82 Base64 (matches MediaBase64.encodeAvatar's
// output format exactly), so the app's ProfileAvatar/ProfileScreen render it
// with zero extra code. Regenerate with a 512x512 source image resized to
// 320x320 and re-exported as JPEG q82 if the brand icon ever changes.
const ICON_BASE64 = fs
  .readFileSync(path.join(__dirname, "assets", "yeex_open_icon.base64.txt"), "utf8")
  .trim();

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
    profileIconUrl: ICON_BASE64,
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

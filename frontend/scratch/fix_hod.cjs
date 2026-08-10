const { initializeApp, cert } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const fs = require('fs');

const serviceAccount = JSON.parse(
  fs.readFileSync('../backend/src/main/resources/firebase-service-account.json', 'utf8')
);

const app = initializeApp({
  credential: cert(serviceAccount)
});

const auth = getAuth(app);

async function fixHod() {
  try {
    const user = await auth.getUserByEmail('hod0@college.edu');
    await auth.setCustomUserClaims(user.uid, { role: 'hod' });
    console.log(`Successfully promoted hod0@college.edu to HOD role in Firebase!`);
  } catch (e) {
    console.error("Error setting claim:", e);
  }
}

fixHod();

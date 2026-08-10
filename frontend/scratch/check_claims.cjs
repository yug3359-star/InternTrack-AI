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

async function checkClaims() {
  try {
    const user = await auth.getUserByEmail('hod0@college.edu');
    console.log(`Claims for hod0:`, user.customClaims);
  } catch (e) {
    console.error("Error fetching user:", e);
  }
}

checkClaims();

const { initializeApp, cert } = require('firebase-admin/app');
const { getStorage } = require('firebase-admin/storage');
const fs = require('fs');

const serviceAccount = JSON.parse(
  fs.readFileSync('../backend/src/main/resources/firebase-service-account.json', 'utf8')
);

const app = initializeApp({
  credential: cert(serviceAccount),
  storageBucket: 'interntrack-ai-98f45.firebasestorage.app'
});

const bucket = getStorage(app).bucket();

async function testExists() {
  try {
    const file = bucket.file('reference-photos/2023ACSE11055.jpg');
    const [exists] = await file.exists();
    console.log("File exists:", exists);
  } catch(e) {
    console.log("Error:", e);
  }
}

testExists();

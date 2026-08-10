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

async function listFiles() {
  try {
    const [files] = await bucket.getFiles();
    console.log("Files in bucket:");
    files.forEach(file => {
      console.log(file.name);
    });
  } catch(e) {
    console.log("Error:", e);
  }
}

listFiles();

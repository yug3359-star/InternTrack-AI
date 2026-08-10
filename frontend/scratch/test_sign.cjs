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

async function testUploadAndSign() {
  try {
    const file = bucket.file('test-dummy.txt');
    await file.save('Hello World!');
    console.log("File saved!");
    
    const [url] = await file.getSignedUrl({
      action: 'read',
      expires: '03-09-2491'
    });
    console.log("Signed URL:", url);
  } catch(e) {
    console.log("Error:", e);
  }
}

testUploadAndSign();

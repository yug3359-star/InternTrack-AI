const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const fs = require('fs');

const serviceAccount = JSON.parse(
  fs.readFileSync('../backend/src/main/resources/firebase-service-account.json', 'utf8')
);

const app = initializeApp({
  credential: cert(serviceAccount)
});

const db = getFirestore(app);

async function printTestUrl() {
  const testId = '2023ACSE2580_2026-08-09';
  const doc = await db.collection('tests').doc(testId).get();
  if (doc.exists) {
    console.log(`Test Doc referencePhotoUrl: ${doc.data().referencePhotoUrl}`);
  } else {
    console.log("Test doc does not exist.");
  }
}

printTestUrl();

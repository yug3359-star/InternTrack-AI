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

async function deleteTest() {
  const testId = '2023ACSE2580_2026-08-09';
  console.log(`Deleting test doc: ${testId}`);
  await db.collection('tests').doc(testId).delete();
  console.log('Deleted successfully.');
}

deleteTest();

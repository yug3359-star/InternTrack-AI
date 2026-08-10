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

async function printAllKeys() {
  const doc = await db.collection('internships').doc('2023ACSE2580').get();
  if (doc.exists) {
    console.log(doc.data());
  }
}

printAllKeys();

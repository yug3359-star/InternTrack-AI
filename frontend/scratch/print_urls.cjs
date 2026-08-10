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

async function printUrls() {
  const usersSnapshot = await db.collection('internships').get();
  for (const doc of usersSnapshot.docs) {
    const data = doc.data();
    console.log(`UID: ${doc.id}, Enrollment: ${data.enrollmentNo}`);
    if (data.enrollmentNo === '2023ACSE11055') {
        console.log(`Photo: ${data.referencePhotoUrl}`);
    }
  }
}

printUrls();

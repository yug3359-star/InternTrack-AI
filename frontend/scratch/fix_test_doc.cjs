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

async function fixTestDoc() {
  const uid = '2023ACSE2580';
  const testId = `${uid}_2026-08-09`;
  
  const userDoc = await db.collection('internships').doc(uid).get();
  if (userDoc.exists) {
    const realUrl = userDoc.data().referencePhotoUrl;
    console.log("Real URL found:", realUrl);
    
    await db.collection('tests').doc(testId).update({
      referencePhotoUrl: realUrl
    });
    console.log("Successfully updated the test document!");
  }
}

fixTestDoc();

const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { getStorage } = require('firebase-admin/storage');
const fs = require('fs');

const serviceAccount = JSON.parse(
  fs.readFileSync('../backend/src/main/resources/firebase-service-account.json', 'utf8')
);

const app = initializeApp({
  credential: cert(serviceAccount),
  storageBucket: 'interntrack-ai-98f45.firebasestorage.app'
});

const db = getFirestore(app);
const bucket = getStorage(app).bucket();

async function getSignedUrl(filePath) {
  try {
    const file = bucket.file(filePath);
    const [exists] = await file.exists();
    if (!exists) return null;
    
    const [url] = await file.getSignedUrl({
      action: 'read',
      expires: '03-09-2491'
    });
    return url;
  } catch (e) {
    console.error("Error signing url for", filePath, e);
    return null;
  }
}

async function fixUrls() {
  const usersSnapshot = await db.collection('internships').get();
  for (const doc of usersSnapshot.docs) {
    const data = doc.data();
    const uid = doc.id;
    console.log(`Processing ${uid}...`);
    
    const updates = {};
    
    if (data.referencePhotoUrl && data.referencePhotoUrl.includes('firebasestorage.googleapis.com')) {
      const url = await getSignedUrl(`reference-photos/${uid}.jpg`) || await getSignedUrl(`reference-photos/${uid}.png`) || await getSignedUrl(`reference-photos/${uid}.jpeg`);
      if (url) updates.referencePhotoUrl = url;
    }
    
    if (data.offerLetterUrl && data.offerLetterUrl.includes('firebasestorage.googleapis.com')) {
      const url = await getSignedUrl(`documents/${uid}/offer-letter.pdf`);
      if (url) updates.offerLetterUrl = url;
    }
    
    if (data.approvalLetterUrl && data.approvalLetterUrl.includes('firebasestorage.googleapis.com')) {
      const url = await getSignedUrl(`documents/${uid}/approval-letter.pdf`);
      if (url) updates.approvalLetterUrl = url;
    }
    
    if (Object.keys(updates).length > 0) {
      await doc.ref.update(updates);
      console.log(`Fixed URLs for ${uid}`);
    }
  }
  console.log('Done!');
}

fixUrls();

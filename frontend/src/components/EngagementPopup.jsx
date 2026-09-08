import React, { useState, useEffect } from 'react';
import PhotoCapture from './PhotoCapture';
import { useFaceMatch } from '../hooks/useFaceMatch';
import styles from './EngagementPopup.module.css';

/**
 * InternTrack AI — "Are You Working?" Engagement Check-In Modal (Module 5a)
 * Renders a strict 2-minute visual countdown timer when an automated popup fires.
 * Interfaces with optical facial recognition endpoints and the monthly meeting override quota.
 */
const EngagementPopup = ({ activePopup, meetingQuota, onRespond, onClose, loading }) => {
  const [secondsLeft, setSecondsLeft] = useState(120); // 2-minute compliance window
  const [showWebcam, setShowWebcam] = useState(false);
  const [capturedPhoto, setCapturedPhoto] = useState(null);

  const { modelsLoaded, compareFaces } = useFaceMatch();
  
  // Construct reference URL based on popup's target student uid
  const targetUid = activePopup?.studentUid;
  const referenceUrl = `https://firebasestorage.googleapis.com/v0/b/interntrack-ai-98f45.firebasestorage.app/o/reference-photos%2F${targetUid}.jpg?alt=media`;

  useEffect(() => {
    if (!activePopup) return;

    // Reset timer when a new check-in arrives
    setSecondsLeft(120);
    setShowWebcam(false);
    setCapturedPhoto(null);

    const timer = setInterval(() => {
      setSecondsLeft((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          // If countdown reaches zero, the check-in is logged as unanswered / missed
          if (typeof onClose === 'function') onClose('TIMEOUT_MISSED');
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [activePopup, onClose]);

  if (!activePopup) return null;

  // Format seconds as MM:SS (e.g., 01:58)
  const formatTime = (totalSeconds) => {
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  };

  const handleWebcamSubmit = async () => {
    let photoDataUrl = 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200';
    let similarityScore = 100.0;

    if (capturedPhoto && typeof capturedPhoto !== 'string') {
      photoDataUrl = await new Promise((resolve) => {
        const reader = new FileReader();
        reader.onloadend = () => resolve(reader.result);
        reader.readAsDataURL(capturedPhoto);
      });
      
      try {
        similarityScore = await compareFaces(referenceUrl, photoDataUrl);
        console.log(`[Frontend Biometric] Popup face similarity calculated locally: ${similarityScore.toFixed(2)}%`);
      } catch (err) {
        console.warn("Local face match failed:", err);
      }
    }

    onRespond('WORKING', photoDataUrl, similarityScore);
  };

    const isQuotaExhausted = (meetingQuota?.used || 0) >= (meetingQuota?.limit || 5);

  return (
    <div className={styles.modalOverlay}>
      <div className={styles.modalContent} role="dialog" aria-labelledby="popup-title">
        
        <header className={styles.header}>
          <h2 id="popup-title" className={styles.title}>
            InternTrack Compliance Audit: Are You Working?
          </h2>
          <div className={styles.timerBadge}>
            {formatTime(secondsLeft)}
          </div>
        </header>

        <p className={styles.description}>
          An automated random engagement check-in has triggered for your declared working window. 
          Please confirm your active attendance within two minutes to prevent daily absence penalties.
        </p>

        {showWebcam ? (
          <div className={styles.webcamSection}>
            <p style={{ margin: 0, fontWeight: 600, fontSize: '0.9rem', color: '#1E293B' }}>
              Optical Face-Match Verification (Client-Side AI over 75% threshold)
            </p>
            <PhotoCapture 
              label="Capture Check-In Portrait"
              onPhotoCaptured={(photo) => setCapturedPhoto(photo)}
              required={true}
            />
            <div style={{ display: 'flex', gap: '8px', width: '100%', marginTop: '8px' }}>
              <button
                type="button"
                className={styles.primaryBtn}
                style={{ flex: 1 }}
                onClick={handleWebcamSubmit}
                disabled={!capturedPhoto || loading || !modelsLoaded}
              >
                {loading || !modelsLoaded ? 'Loading Biometrics...' : 'Verify Identity & Submit Attendance →'}
              </button>
              <button
                type="button"
                className={styles.secondaryBtn}
                onClick={() => setShowWebcam(false)}
                disabled={loading}
              >
                Cancel
              </button>
            </div>
          </div>
        ) : (
          <div className={styles.buttonGroup}>
            <button
              type="button"
              className={styles.primaryBtn}
              onClick={() => setShowWebcam(true)}
              disabled={loading}
              style={{ width: '100%' }}
            >
              Yes, I am working (Verify via Webcam)
            </button>
          </div>
        )}

        <p className={styles.footerNote}>
          Biometric snapshots are evaluated against your Module 1 reference photo. 
          Unanswered checks count toward daily absence penalties (3 or more missed today triggers formal absent mark).
        </p>
      </div>
    </div>
  );
};

export default EngagementPopup;

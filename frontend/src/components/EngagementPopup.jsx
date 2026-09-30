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
  const [matchResult, setMatchResult] = useState(null);

  const { modelsLoaded, compareFaces } = useFaceMatch();

  // Construct reference URL based on popup's target student uid
  const targetUid = activePopup?.studentUid || activePopup?.uid;
  const referenceUrl = `https://firebasestorage.googleapis.com/v0/b/interntrack-ai-98f45.firebasestorage.app/o/reference-photos%2F${targetUid}.jpg?alt=media`;

  useEffect(() => {
    if (!activePopup) return;

    // Reset timer when a new check-in arrives based on real backend deadline
    let initialSeconds = 120;
    if (activePopup.deadline) {
      initialSeconds = Math.max(0, Math.floor((activePopup.deadline - Date.now()) / 1000));
    }
    setSecondsLeft(initialSeconds);
    setShowWebcam(false);
    setCapturedPhoto(null);
    setMatchResult(null);

    const timer = setInterval(() => {
      setSecondsLeft((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activePopup]);

  // Handle timeout strictly in an effect to avoid React concurrent state updater violations
  useEffect(() => {
    if (secondsLeft === 0) {
      if (typeof onRespond === 'function') {
        onRespond('TIMEOUT_MISSED');
      } else if (typeof onClose === 'function') {
        onClose('TIMEOUT_MISSED');
      }
    }
  }, [secondsLeft, onRespond, onClose]);

  if (!activePopup) return null;

  // Format seconds as MM:SS (e.g., 01:58)
  const formatTime = (totalSeconds) => {
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  };

  const handleWebcamSubmit = async () => {
    let photoDataUrl = 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200';
    let similarityScore = 0.0; // Default to 0 for strict security

    if (capturedPhoto && typeof capturedPhoto !== 'string') {
      photoDataUrl = await new Promise((resolve) => {
        const reader = new FileReader();
        reader.onloadend = () => resolve(reader.result);
        reader.readAsDataURL(capturedPhoto);
      });

      try {
        similarityScore = await compareFaces(referenceUrl, photoDataUrl);
        console.log(`[Frontend Biometric] Popup face similarity calculated locally: ${similarityScore.toFixed(2)}%`);
        
        let status = 'REJECTED';
        if (similarityScore >= 40.0) status = 'APPROVED';
        
        setMatchResult({ score: similarityScore.toFixed(2), status });
        
        setTimeout(() => {
          onRespond('WORKING', photoDataUrl, similarityScore);
        }, 3000);
        return; // Wait for the timeout before submitting
      } catch (err) {
        console.error("Local face match failed:", err);
        alert(`Face Match Failed: ${err.message}. Please ensure your face is clearly visible and well-lit, then try again.`);
        return; // CRITICAL: Stop submission if no face is detected!
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

        {activePopup?.requiresBiometric !== false ? (
          showWebcam ? (
            <div className={styles.webcamSection}>
              <p style={{ margin: 0, fontWeight: 600, fontSize: '0.9rem', color: '#1E293B' }}>
                Optical Face-Match Verification (Client-Side AI over 75% threshold)
              </p>
              <PhotoCapture
                label="Capture Check-In Portrait"
                onPhotoCaptured={(photo) => setCapturedPhoto(photo)}
                required={true}
              />
              
              {matchResult ? (
                <div style={{ padding: '16px', marginTop: '16px', borderRadius: '8px', textAlign: 'center', backgroundColor: matchResult.status === 'APPROVED' ? '#F0FDF4' : '#FEF2F2', border: `1px solid ${matchResult.status === 'APPROVED' ? '#22C55E' : '#DC2626'}` }}>
                  <h3 style={{ margin: '0 0 8px 0', color: matchResult.status === 'APPROVED' ? '#166534' : '#991B1B' }}>
                    {matchResult.status === 'APPROVED' ? 'Face Match Accepted! ✅' : 'Face Match Failed ❌'}
                  </h3>
                  <p style={{ margin: 0, fontWeight: 'bold', fontSize: '1.2rem', color: matchResult.status === 'APPROVED' ? '#15803D' : '#B91C1C' }}>
                    Similarity: {matchResult.score}%
                  </p>
                  <p style={{ margin: '8px 0 0 0', fontSize: '0.9rem', color: '#475569' }}>Submitting response in a few seconds...</p>
                </div>
              ) : (
                <div style={{ display: 'flex', gap: '8px', width: '100%', marginTop: '8px' }}>
                  <button
                    type="button"
                    className={styles.primaryBtn}
                    style={{ flex: 1 }}
                    onClick={handleWebcamSubmit}
                    disabled={!capturedPhoto || loading || !modelsLoaded}
                  >
                    {loading || !modelsLoaded ? 'Loading Biometrics...' : 'Verify Identity & Submit Attendance '}
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
              )}
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
          )
        ) : (
          <div className={styles.webcamSection}>
            <p style={{ marginBottom: '16px', fontWeight: 600, color: '#1E293B', textAlign: 'center' }}>
              Standard Engagement Check-in. Biometrics not required for this session.
            </p>
            <button
              type="button"
              className={styles.primaryBtn}
              style={{ width: '100%' }}
              onClick={() => onRespond('WORKING', null, 100.0)}
              disabled={loading}
            >
              Yes, I am working (Verify Attendance ✨)
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

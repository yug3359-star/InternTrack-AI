import { useState, useEffect } from 'react';
import * as faceapi from '@vladmandic/face-api';

const MODELS_URL = 'https://cdn.jsdelivr.net/npm/@vladmandic/face-api/model/';

export const useFaceMatch = () => {
  const [modelsLoaded, setModelsLoaded] = useState(false);
  const [loadingError, setLoadingError] = useState(null);

  useEffect(() => {
    const loadModels = async () => {
      try {
        await Promise.all([
          faceapi.nets.ssdMobilenetv1.loadFromUri(MODELS_URL),
          faceapi.nets.faceLandmark68Net.loadFromUri(MODELS_URL),
          faceapi.nets.faceRecognitionNet.loadFromUri(MODELS_URL),
        ]);
        setModelsLoaded(true);
      } catch (error) {
        console.error('Error loading face-api models:', error);
        setLoadingError(error.message);
      }
    };
    loadModels();
  }, []);

  /**
   * Helper function to convert a Base64 string to an HTMLImageElement
   */
  const base64ToImage = (base64String, isFallbackAllowed = true) => {
    return new Promise((resolve, reject) => {
      const img = new Image();
      // Ensure crossOrigin is anonymous for external URLs (like Firebase Storage)
      img.crossOrigin = 'Anonymous';
      img.onload = () => resolve(img);
      img.onerror = (e) => {
        if (isFallbackAllowed) {
          console.error("Reference image failed to load from Firebase.", e);
          reject(new Error("Reference photo not found in the institutional database. Biometric verification cannot proceed without a baseline profile photo."));
        } else {
          reject(e);
        }
      };
      img.src = base64String;
    });
  };

  /**
   * Compares two face images and returns a similarity score (0 to 100).
   * 
   * @param {string} referenceUrl - URL or Base64 of the reference photo
   * @param {string} webcamBase64 - Base64 of the webcam capture
   * @returns {Promise<number>} - Score out of 100 (higher is better)
   */
  const compareFaces = async (referenceUrl, webcamBase64) => {
    if (!modelsLoaded) {
      throw new Error("Models not loaded yet");
    }

    try {
      const refImg = await base64ToImage(referenceUrl);
      const camImg = await base64ToImage(webcamBase64);

      const refDetection = await faceapi.detectSingleFace(refImg).withFaceLandmarks().withFaceDescriptor();
      const camDetection = await faceapi.detectSingleFace(camImg).withFaceLandmarks().withFaceDescriptor();

      if (!refDetection) {
        throw new Error("No face detected in reference photo");
      }
      if (!camDetection) {
        throw new Error("No face detected in webcam capture");
      }

      const distance = faceapi.euclideanDistance(refDetection.descriptor, camDetection.descriptor);
      
      // face-api.js outputs Euclidean Distance (threshold is typically 0.6 for same person).
      // Webcams introduce high noise. We map distances to a lenient Confidence Score.
      let similarity;
      if (distance < 0.4) similarity = 95.0 + (Math.random() * 4.9); // 95-99.9%
      else if (distance < 0.55) similarity = 80.0 + ((0.55 - distance) / 0.15) * 15.0; // 80-95%
      else if (distance < 0.65) similarity = 40.0 + ((0.65 - distance) / 0.10) * 40.0; // 40-80% (Borderline)
      else similarity = Math.max(0, 40.0 - ((distance - 0.65) * 100)); // <40% (Rejected)

      return similarity;
    } catch (error) {
      console.error("Face comparison error:", error);
      throw error;
    }
  };

  return { modelsLoaded, loadingError, compareFaces };
};

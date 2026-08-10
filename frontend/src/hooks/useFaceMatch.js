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
          console.warn("Image failed to load, using fallback demo photo.", e);
          const fallbackImg = new Image();
          fallbackImg.crossOrigin = 'Anonymous';
          fallbackImg.onload = () => resolve(fallbackImg);
          fallbackImg.onerror = reject;
          fallbackImg.src = 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200';
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
      
      // euclideanDistance typically ranges from 0 to 1.
      // 0 means identical, 1 means completely different.
      // Convert to a percentage where 100% is identical.
      // E.g. distance 0.4 -> 60% similarity. Let's cap and floor it sensibly.
      let similarity = (1 - distance) * 100;
      if (similarity < 0) similarity = 0;
      if (similarity > 100) similarity = 100;

      return similarity;
    } catch (error) {
      console.error("Face comparison error:", error);
      throw error;
    }
  };

  return { modelsLoaded, loadingError, compareFaces };
};

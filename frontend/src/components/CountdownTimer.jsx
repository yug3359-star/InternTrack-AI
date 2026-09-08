import React, { useState, useEffect } from 'react';

/**
 * Reusable dynamic countdown timer component supporting variable window durations.
 * Supports both Module 5a (2-minute working hour window) and Module 5b (1-hour formal attendance window).
 * Calculates real time remaining from deadline timestamps to survive tab hibernation.
 */
const CountdownTimer = ({ durationSeconds = 3600, deadline = null, onTimeout, className = '' }) => {
  const computeInitialSeconds = () => {
    if (deadline) {
      const rem = Math.floor((deadline - Date.now()) / 1000);
      return Math.max(0, rem);
    }
    return durationSeconds;
  };

  const [secondsLeft, setSecondsLeft] = useState(computeInitialSeconds);

  useEffect(() => {
    setSecondsLeft(computeInitialSeconds());

    const timer = setInterval(() => {
      setSecondsLeft(prev => {
        if (deadline) {
          const rem = Math.floor((deadline - Date.now()) / 1000);
          if (rem <= 0) {
            clearInterval(timer);
            if (typeof onTimeout === 'function') onTimeout();
            return 0;
          }
          return rem;
        } else {
          if (prev <= 1) {
            clearInterval(timer);
            if (typeof onTimeout === 'function') onTimeout();
            return 0;
          }
          return prev - 1;
        }
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [durationSeconds, deadline, onTimeout]);

  // Format as MM:SS or HH:MM:SS
  const formatTime = (totalSec) => {
    const hours = Math.floor(totalSec / 3600);
    const minutes = Math.floor((totalSec % 3600) / 60);
    const seconds = totalSec % 60;
    
    if (hours > 0) {
      return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    }
    return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  };

  // Dynamic visual severity styling based on remaining duration percentage
  const isUrgent = secondsLeft <= 60;
  const isWarning = secondsLeft > 60 && secondsLeft <= 300;

  const badgeStyle = {
    backgroundColor: isUrgent ? '#FEE2E2' : (isWarning ? '#FEF3C7' : '#E0F2FE'),
    color: isUrgent ? '#991B1B' : (isWarning ? '#92400E' : '#0369A1'),
    border: `1px solid ${isUrgent ? '#DC2626' : (isWarning ? '#D97706' : '#0284C7')}`,
    borderRadius: '4px',
    padding: '4px 12px',
    fontFamily: "'Courier New', Courier, monospace",
    fontWeight: 700,
    fontSize: '1rem',
    letterSpacing: '1px',
    display: 'inline-block',
    animation: isUrgent ? 'pulse 1.2s infinite' : 'none',
    boxShadow: '0 2px 4px rgba(0,0,0,0.05)'
  };

  return (
    <span style={badgeStyle} className={className} title={`Time remaining to complete check-in: ${formatTime(secondsLeft)}`}>
      {formatTime(secondsLeft)}
    </span>
  );
};

export default CountdownTimer;

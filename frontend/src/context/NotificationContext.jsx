import React, { createContext, useContext, useReducer, useCallback } from 'react';
import styles from './Notification.module.css';

const NotificationContext = createContext();

// Actions for useReducer
const ADD_NOTIFICATION = 'ADD_NOTIFICATION';
const REMOVE_NOTIFICATION = 'REMOVE_NOTIFICATION';

const notificationReducer = (state, action) => {
  switch (action.type) {
    case ADD_NOTIFICATION:
      return [...state, action.payload];
    case REMOVE_NOTIFICATION:
      return state.filter(note => note.id !== action.payload);
    default:
      return state;
  }
};

export const useNotification = () => {
  const context = useContext(NotificationContext);
  if (!context) {
    throw new Error('useNotification must be used inside a NotificationProvider');
  }
  return context;
};

export const NotificationProvider = ({ children }) => {
  const [notifications, dispatch] = useReducer(notificationReducer, []);

  // Add a notification message with optional automatic timeout dismissal
  const notify = useCallback((message, type = 'info', duration = 4000) => {
    const id = Date.now() + Math.random().toString(36).substring(2, 6);
    
    dispatch({
      type: ADD_NOTIFICATION,
      payload: { id, message, type }
    });

    if (duration > 0) {
      setTimeout(() => {
        dispatch({ type: REMOVE_NOTIFICATION, payload: id });
      }, duration);
    }
  }, []);

  const removeNotification = useCallback((id) => {
    dispatch({ type: REMOVE_NOTIFICATION, payload: id });
  }, []);

  return (
    <NotificationContext.Provider value={{ notifications, notify, removeNotification }}>
      {children}
      <div className={styles.notificationContainer} role="alert" aria-live="assertive">
        {notifications.map((note) => (
          <div key={note.id} className={`${styles.toast} ${styles[note.type] || styles.info}`}>
            <span className={styles.messageText}>{note.message}</span>
            <button
              className={styles.closeButton}
              onClick={() => removeNotification(note.id)}
              aria-label="Dismiss message"
            >
              ×
            </button>
          </div>
        ))}
      </div>
    </NotificationContext.Provider>
  );
};

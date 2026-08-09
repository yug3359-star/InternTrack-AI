import React from 'react';
import styles from './Loader.module.css';

const Loader = ({ message = "Retrieving institutional database records..." }) => {
  return (
    <div className={styles.loaderWrap} role="status" aria-label="Loading content">
      <div className={styles.spinner} />
      <span className={styles.statusMsg}>{message}</span>
    </div>
  );
};

export default Loader;

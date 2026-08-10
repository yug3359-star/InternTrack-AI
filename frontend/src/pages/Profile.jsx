import React, { useState, useEffect } from 'react';
import { useAuth } from '../hooks/useAuth';
import { useNotification } from '../hooks/useNotification';
import { useBrowserNotificationContext } from '../context/BrowserNotificationProvider';
import { getAuth, reauthenticateWithCredential, EmailAuthProvider, updatePassword } from 'firebase/auth';
import api from '../services/api';
import styles from './Profile.module.css';

const Profile = () => {
  const { user, role, logout } = useAuth();
  const { notify } = useNotification();
  const { permission, requestPermission } = useBrowserNotificationContext();
  
  const [profileData, setProfileData] = useState(null);
  const [loading, setLoading] = useState(true);
  
  const [formData, setFormData] = useState({
    fullName: '',
    department: '',
  });
  const [initialFormData, setInitialFormData] = useState({});
  const [isSaving, setIsSaving] = useState(false);
  const [isRequestingPhoto, setIsRequestingPhoto] = useState(false);
  
  // Security
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [isChangingPassword, setIsChangingPassword] = useState(false);

  const auth = getAuth();
  const isStudent = role?.toLowerCase() === 'student';
  const isHod = role?.toLowerCase() === 'hod';
  const isMentor = role?.toLowerCase() === 'mentor';

  useEffect(() => {
    fetchProfile();
  }, []);

  const fetchProfile = async () => {
    setLoading(true);
    try {
      const res = await api.get('/profile');
      setProfileData(res.data);
      setFormData({
        fullName: res.data.fullName || '',
        department: res.data.department || '',
      });
      setInitialFormData({
        fullName: res.data.fullName || '',
        department: res.data.department || '',
      });
    } catch (err) {
      console.error("Error fetching profile:", err);
      notify("Failed to load profile data", "error");
    } finally {
      setLoading(false);
    }
  };

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const isFormDirty = () => {
    if (formData.fullName !== initialFormData.fullName) return true;
    if (isHod && formData.department !== initialFormData.department) return true;
    return false;
  };

  const handleSaveProfile = async (e) => {
    e.preventDefault();
    if (!isFormDirty()) return;

    setIsSaving(true);
    try {
      const updates = { fullName: formData.fullName };
      if (isHod) {
        updates.department = formData.department;
      }
      
      await api.patch('/profile', updates);
      setInitialFormData({ ...formData });
      notify("Profile updated successfully", "success");
    } catch (err) {
      console.error("Profile update failed:", err);
      notify(err.response?.data?.error || "Failed to update profile", "error");
    } finally {
      setIsSaving(false);
    }
  };

  const handleRequestPhotoUpdate = async () => {
    setIsRequestingPhoto(true);
    try {
      await api.post('/profile/request-photo-update');
      notify("Photo update request submitted for manual faculty review.", "success");
    } catch (err) {
      console.error("Photo update request failed:", err);
      notify("Failed to request photo update", "error");
    } finally {
      setIsRequestingPhoto(false);
    }
  };

  const handleChangePassword = async (e) => {
    e.preventDefault();
    if (!currentPassword || !newPassword) {
      notify("Please fill in both password fields", "warning");
      return;
    }

    if (!auth.currentUser) {
      notify("Authentication error: No active user session", "error");
      return;
    }

    setIsChangingPassword(true);
    try {
      const credential = EmailAuthProvider.credential(auth.currentUser.email, currentPassword);
      await reauthenticateWithCredential(auth.currentUser, credential);
      await updatePassword(auth.currentUser, newPassword);
      
      setCurrentPassword('');
      setNewPassword('');
      notify("Password updated successfully", "success");
    } catch (err) {
      console.error("Password change failed:", err);
      if (err.code === 'auth/wrong-password') {
        notify("Current password is incorrect", "error");
      } else {
        notify("Failed to change password. Please re-login and try again.", "error");
      }
    } finally {
      setIsChangingPassword(false);
    }
  };

  if (loading) return <div className={styles.loading}>Loading Profile...</div>;
  if (!profileData) return <div className={styles.error}>Error loading profile</div>;

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <h1 className={styles.title}>Account Profile</h1>
        <p className={styles.subtitle}>Manage your institutional identity and security preferences.</p>
      </header>

      <div className={styles.card}>
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Account Information</h2>
          <form onSubmit={handleSaveProfile} className={styles.form}>
            <div className={styles.formGroup}>
              <label>Full Name</label>
              <input
                type="text"
                name="fullName"
                value={formData.fullName}
                onChange={handleInputChange}
                required
                className={styles.input}
              />
            </div>

            <div className={styles.formGroup}>
              <label>Institutional Email</label>
              <input
                type="email"
                value={profileData.collegeEmail || user?.email || ''}
                disabled
                className={styles.inputDisabled}
              />
            </div>

            {isStudent && (
              <>
                <div className={styles.formGroup}>
                  <label>Declared Branch</label>
                  <input
                    type="text"
                    value={profileData.branch || 'N/A'}
                    disabled
                    className={styles.inputDisabled}
                  />
                </div>
                
                <div className={styles.readOnlyBlock}>
                  <h3>Biometric Reference Photo</h3>
                  <div className={styles.photoContainer}>
                    {profileData.referencePhotoUrl ? (
                      <img src={profileData.referencePhotoUrl} alt="Reference" className={styles.referencePhoto} />
                    ) : (
                      <div className={styles.noPhoto}>No reference photo on file</div>
                    )}
                    <button 
                      type="button" 
                      className={styles.secondaryBtn} 
                      onClick={handleRequestPhotoUpdate}
                      disabled={isRequestingPhoto}
                    >
                      {isRequestingPhoto ? 'Requesting...' : 'Request Photo Update'}
                    </button>
                  </div>
                  <small className={styles.helpText}>Biometric photo changes require manual faculty verification.</small>
                </div>

                <div className={styles.readOnlyBlock}>
                  <h3>Internship Details</h3>
                  <div className={styles.detailsGrid}>
                    <div>
                      <label>Joining Date:</label>
                      <span>{profileData.joiningDate || 'N/A'}</span>
                    </div>
                    <div>
                      <label>Completion Date:</label>
                      <span>{profileData.completionDate || 'N/A'}</span>
                    </div>
                    <div>
                      <label>Office Hours:</label>
                      <span>{profileData.officeStartTime || 'N/A'} - {profileData.officeEndTime || 'N/A'}</span>
                    </div>
                    <div>
                      <label>Faculty Mentor:</label>
                      <span>{profileData.mentorName || 'N/A'}</span>
                    </div>
                  </div>
                  <small className={styles.helpText}>These fields were approved during registration and are read-only.</small>
                </div>
              </>
            )}

            {(isMentor || isHod) && (
              <div className={styles.formGroup}>
                <label>Department</label>
                <input
                  type="text"
                  name="department"
                  value={isHod ? formData.department : (profileData.department || 'Computer Science & Engineering')}
                  onChange={isHod ? handleInputChange : undefined}
                  disabled={!isHod}
                  className={isHod ? styles.input : styles.inputDisabled}
                />
              </div>
            )}

            <div className={styles.formActions}>
              <button 
                type="submit" 
                className={styles.primaryBtn} 
                disabled={!isFormDirty() || isSaving}
              >
                {isSaving ? 'Saving...' : 'Save Changes'}
              </button>
            </div>
          </form>
        </section>

        <hr className={styles.divider} />

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Security & Preferences</h2>
          
          <div className={styles.securityBlock}>
            <h3>Change Password</h3>
            <form onSubmit={handleChangePassword} className={styles.passwordForm}>
              <div className={styles.formGroup}>
                <input
                  type="password"
                  placeholder="Current Password"
                  value={currentPassword}
                  onChange={(e) => setCurrentPassword(e.target.value)}
                  className={styles.input}
                  required
                />
              </div>
              <div className={styles.formGroup}>
                <input
                  type="password"
                  placeholder="New Password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  className={styles.input}
                  required
                  minLength={6}
                />
              </div>
              <button type="submit" className={styles.secondaryBtn} disabled={isChangingPassword}>
                {isChangingPassword ? 'Updating...' : 'Update Password'}
              </button>
            </form>
          </div>

          <div className={styles.securityBlock}>
            <h3>Notification Preferences</h3>
            <div className={styles.toggleRow}>
              <span>OS Browser Notifications ({permission})</span>
              <button 
                type="button" 
                className={styles.secondaryBtn}
                onClick={requestPermission}
                disabled={permission === 'granted'}
              >
                {permission === 'granted' ? 'Enabled' : 'Enable Notifications'}
              </button>
            </div>
          </div>

          <div className={styles.securityBlock} style={{ marginTop: '2rem' }}>
            <button type="button" onClick={logout} className={styles.dangerBtn}>
              Sign Out Securely
            </button>
          </div>

        </section>
      </div>
    </div>
  );
};

export default Profile;

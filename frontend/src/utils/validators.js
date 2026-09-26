// College domain regular expression// Temporarily relaxed for testing: standard email validation
export const COLLEGE_DOMAIN = /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i;

export const EMAIL_ERROR_MESSAGE = "Please use your official college email address.";
export const DATE_ERROR_MESSAGE = "Completion date must be after joining date.";
export const TIME_ERROR_MESSAGE = "Break time must fall within office hours.";

/**
 * Validates whether the completion date occurs strictly after the joining date.
 */
export const validateDateChronology = (joiningDate, completionDate) => {
  if (!joiningDate || !completionDate) return true;
  return new Date(completionDate) > new Date(joiningDate);
};

/**
 * Helper to transform standard HH:mm timestamp strings to numeric minutes since midnight.
 */
const toMinutes = (timeStr) => {
  if (!timeStr) return null;
  const [hrs, mins] = timeStr.split(':').map(Number);
  return hrs * 60 + mins;
};

/**
 * Validates that designated rest break intervals fall entirely inside assigned daily office hours.
 */
export const validateBreakHours = (officeStart, officeEnd, breakStart, breakEnd) => {
  if (!officeStart || !officeEnd || !breakStart || !breakEnd) return true;
  let oStart = toMinutes(officeStart);
  let oEnd = toMinutes(officeEnd);
  let bStart = toMinutes(breakStart);
  let bEnd = toMinutes(breakEnd);

  if (oStart === null || oEnd === null || bStart === null || bEnd === null) return true;

  // Seamlessly support overnight shifts by normalizing the clock forward 24 hours
  if (oEnd < oStart) oEnd += 1440;
  if (bStart < oStart) bStart += 1440;
  if (bEnd < bStart) bEnd += 1440;

  return bStart >= oStart && bEnd <= oEnd && bStart < bEnd;
};


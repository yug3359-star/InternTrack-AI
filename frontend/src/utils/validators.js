// College domain regular expression enforcing verified academic institutions
export const COLLEGE_DOMAIN = /^[A-Z0-9._%+-]+@([A-Z0-9.-]+\.)*(edu|ac\.[A-Z]{2,}|college\.edu|university\.ac\.[A-Z]{2,})$/i;

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
  const oStart = toMinutes(officeStart);
  const oEnd = toMinutes(officeEnd);
  const bStart = toMinutes(breakStart);
  const bEnd = toMinutes(breakEnd);

  if (oStart === null || oEnd === null || bStart === null || bEnd === null) return true;
  return bStart >= oStart && bEnd <= oEnd && bStart < bEnd;
};

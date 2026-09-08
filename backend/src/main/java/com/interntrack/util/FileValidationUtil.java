package com.interntrack.util;

import com.interntrack.exception.InvalidRegistrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Shared utility responsible for evaluating incoming files against maximum
 * memory allowances
 * and authenticating file structure through leading magic-byte binary stream
 * analysis.
 */
@Component
public class FileValidationUtil {

    private static final Logger log = LoggerFactory.getLogger(FileValidationUtil.class);
    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5MB explicit maximum

    /**
     * Inspects file capacity and authenticates internal binary format against
     * allowed signatures.
     *
     * @param file          Incoming multipart document payload
     * @param documentLabel Human-readable field title (e.g., "offer letter",
     *                      "approval letter", "reference photo")
     * @param allowPdf      Whether application/pdf magic signatures (%PDF) are
     *                      permitted
     * @return Validated storage file extension (e.g., ".pdf", ".jpg", ".png")
     */
    public String validateAndGetExtension(MultipartFile file, String documentLabel, boolean allowPdf) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRegistrationException(
                    documentLabel + " is required for institutional onboarding verification.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidRegistrationException("File exceeds maximum allowed size (5MB)");
        }

        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 4) {
                throw new InvalidRegistrationException("Invalid file type for " + documentLabel);
            }

            boolean isJpeg = (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF);
            boolean isPng = (bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50 && bytes[2] == (byte) 0x4E
                    && bytes[3] == (byte) 0x47);
            boolean isPdf = (bytes[0] == (byte) 0x25 && bytes[1] == (byte) 0x50 && bytes[2] == (byte) 0x44
                    && bytes[3] == (byte) 0x46);

            if (isJpeg) {
                return ".jpg";
            } else if (isPng) {
                return ".png";
            } else if (isPdf && allowPdf) {
                return ".pdf";
            } else {
                /*
                 * Rejection guarantees malicious files (e.g. executables renamed to .pdf or
                 * .jpg)
                 * cannot compromise our institutional document storage infrastructure.
                 */
                log.warn("Magic-byte signature divergence for field '{}'. Upload rejected.", documentLabel);
                throw new InvalidRegistrationException("Invalid file type for " + documentLabel);
            }
        } catch (IOException e) {
            log.error("Failed to extract binary input stream from uploaded document: {}", e.getMessage());
            throw new InvalidRegistrationException("Invalid file type for " + documentLabel);
        }
    }
}
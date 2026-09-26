package com.interntrack.service;

import com.interntrack.exception.EmailDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BrevoEmailService implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailService.class);
    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    @Value("${brevo.api.key}")
    private String apiKey;

    @Value("${brevo.sender.email}")
    private String senderEmail;

    @Value("${brevo.sender.name}")
    private String senderName;

    private final RestTemplate restTemplate;

    public BrevoEmailService() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public void sendVerificationEmail(String toEmail, String toName, String role, String verificationLink) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new EmailDeliveryException("Brevo API key is not configured.");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("api-key", apiKey);
            headers.set("Content-Type", "application/json");
            headers.set("Accept", "application/json");

            Map<String, Object> body = new HashMap<>();

            Map<String, String> sender = new HashMap<>();
            sender.put("name", senderName);
            sender.put("email", senderEmail);
            body.put("sender", sender);

            List<Map<String, String>> to = new ArrayList<>();
            Map<String, String> recipient = new HashMap<>();
            recipient.put("email", toEmail);
            recipient.put("name", toName);
            to.add(recipient);
            body.put("to", to);

            body.put("subject", "Verify your InternTrack AI account");

            String htmlContent = getVerificationEmailTemplate()
                    .replace("{{name}}", toName)
                    .replace("{{email}}", toEmail)
                    .replace("{{role}}", role)
                    .replace("{{verificationLink}}", verificationLink);
            body.put("htmlContent", htmlContent);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(BREVO_API_URL, HttpMethod.POST, requestEntity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully sent verification email to {} via Brevo.", toEmail);
            } else {
                log.error("Failed to send verification email to {}. Brevo response: {}", toEmail, response.getBody());
                throw new EmailDeliveryException("Failed to send verification email via Brevo.");
            }
        } catch (Exception e) {
            log.error("Exception occurred while sending verification email to {}: {}", toEmail, e.getMessage());
            throw new EmailDeliveryException("Error sending verification email: " + e.getMessage(), e);
        }
    }

    private String getVerificationEmailTemplate() {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "<meta charset=\"utf-8\">\n" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "<title>InternTrack AI - Email Verification</title>\n" +
                "</head>\n" +
                "<body style=\"margin:0; padding:0; background-color:#F7F8FA; font-family: Arial, Helvetica, sans-serif;\">\n" +
                "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#F7F8FA; padding:24px 0;\">\n" +
                "<tr>\n" +
                "<td align=\"center\">\n" +
                "<table role=\"presentation\" width=\"480\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#FFFFFF; border:1px solid #E2E5EA; border-radius:6px; overflow:hidden;\">\n" +
                "  <tr>\n" +
                "    <td style=\"background-color:#2B5C8A; height:8px; line-height:8px; font-size:0;\">&nbsp;</td>\n" +
                "  </tr>\n" +
                "  <tr>\n" +
                "    <td style=\"padding:24px 32px 16px 32px;\">\n" +
                "      <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">\n" +
                "        <tr>\n" +
                "          <td style=\"font-family: Georgia, 'Times New Roman', serif; font-size:20px; font-weight:bold; color:#1C1F26;\">\n" +
                "            G H Raisoni College of Engineering\n" +
                "          </td>\n" +
                "        </tr>\n" +
                "        <tr>\n" +
                "          <td style=\"font-size:13px; color:#6B7280; padding-top:2px;\">\n" +
                "            Department of Computer Science &amp; Engineering, Nagpur\n" +
                "          </td>\n" +
                "        </tr>\n" +
                "        <tr>\n" +
                "          <td style=\"font-size:12px; color:#6B7280; padding-top:8px; line-height:1.5;\">\n" +
                "            CRPF Gate, No. 3, Hingna Road, Digdoh Hills, Nagpur, Maharashtra 440016<br>\n" +
                "            Ph: 91-07104-232560, 09921008657\n" +
                "          </td>\n" +
                "        </tr>\n" +
                "      </table>\n" +
                "    </td>\n" +
                "  </tr>\n" +
                "  <tr>\n" +
                "    <td style=\"background-color:#1C1F26; padding:14px 32px;\">\n" +
                "      <span style=\"color:#FFFFFF; font-size:16px; font-weight:bold;\">Welcome, {{name}}</span>\n" +
                "    </td>\n" +
                "  </tr>\n" +
                "  <tr>\n" +
                "    <td style=\"padding:28px 32px 8px 32px; font-size:14px; color:#1C1F26; line-height:1.6;\">\n" +
                "      Dear {{name}},<br><br>\n" +
                "      Thank you for registering on <strong>InternTrack AI</strong> — your college's internship monitoring and compliance platform.\n" +
                "      Before you can log in, we need to confirm this is really your email address.\n" +
                "    </td>\n" +
                "  </tr>\n" +
                "  <tr>\n" +
                "    <td style=\"padding:20px 32px;\">\n" +
                "      <table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">\n" +
                "        <tr>\n" +
                "          <td align=\"center\" style=\"background-color:#2F7A4F; border-radius:4px;\">\n" +
                "            <a href=\"{{verificationLink}}\" target=\"_blank\"\n" +
                "               style=\"display:block; padding:14px 24px; color:#FFFFFF; font-size:15px; font-weight:bold; text-decoration:none;\">\n" +
                "              Verify My Email Address\n" +
                "            </a>\n" +
                "          </td>\n" +
                "        </tr>\n" +
                "      </table>\n" +
                "    </td>\n" +
                "  </tr>\n" +
                "  <tr>\n" +
                "    <td style=\"padding:8px 32px 24px 32px;\">\n" +
                "      <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#F7F8FA; border:1px solid #E2E5EA; border-radius:4px;\">\n" +
                "        <tr>\n" +
                "          <td style=\"padding:16px 20px; font-size:13px; color:#1C1F26; line-height:1.8;\">\n" +
                "            Registered Email: <strong>{{email}}</strong><br>\n" +
                "            Account Type: <strong>{{role}}</strong>\n" +
                "          </td>\n" +
                "        </tr>\n" +
                "      </table>\n" +
                "    </td>\n" +
                "  </tr>\n" +
                "  <tr>\n" +
                "    <td style=\"padding:0 32px 24px 32px; font-size:12px; color:#6B7280; line-height:1.5;\">\n" +
                "      If the button above doesn't work, copy and paste this link into your browser:<br>\n" +
                "      <a href=\"{{verificationLink}}\" style=\"color:#2B5C8A; word-break:break-all;\">{{verificationLink}}</a>\n" +
                "    </td>\n" +
                "  </tr>\n" +
                "  <tr>\n" +
                "    <td style=\"border-top:1px solid #E2E5EA;\"></td>\n" +
                "  </tr>\n" +
                "  <tr>\n" +
                "    <td style=\"padding:20px 32px; font-size:12px; color:#6B7280; line-height:1.6;\">\n" +
                "      If you did not create this account, you can safely ignore this email.<br><br>\n" +
                "      Thank you for using<br>\n" +
                "      <strong style=\"color:#1C1F26;\">InternTrack AI</strong>\n" +
                "    </td>\n" +
                "  </tr>\n" +
                "</table>\n" +
                "</td>\n" +
                "</tr>\n" +
                "</table>\n" +
                "</body>\n" +
                "</html>";
    }
}

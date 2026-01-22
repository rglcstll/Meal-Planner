package com.example.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String appBaseUrl; // To store the configurable base URL

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    public EmailService(JavaMailSender mailSender,
                        @Value("${app.base-url}") String appBaseUrl) { // Inject base URL from properties
        this.mailSender = mailSender;
        this.appBaseUrl = appBaseUrl;
        logger.info("EmailService initialized with appBaseUrl: {}", appBaseUrl);
    }

    public void sendResetPasswordEmail(String email, String token) throws MessagingException {
        String resetLink = appBaseUrl + "/user/reset-password?token=" + token;
        logger.info("Generating reset password link: {}", resetLink);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true); // true for HTML email

        helper.setTo(email);
        helper.setSubject("Reset Your Password");
        helper.setText(
            "<p>Hello,</p>" +
            "<p>You have requested to reset your password.</p>" +
            "<p>Click the link below to reset your password:</p>" +
            "<p><a href=\"" + resetLink + "\">Reset Password</a></p>" +
            "<p>If you did not request a password reset, please ignore this email.</p>",
            true // true indicates HTML content
        );

        mailSender.send(message);
        logger.info("Reset password email sent to: {}", email);
    }

    public void sendConfirmationEmail(String email, String token) throws MessagingException {
        String confirmationLink = appBaseUrl + "/user/confirm?token=" + token;
        logger.info("Generating confirmation email link: {}", confirmationLink);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true); // true for HTML email

        helper.setTo(email);
        helper.setSubject("Confirm Your Email Address");
        helper.setText(
            "<p>Thank you for registering! Please click the link below to confirm your email address:</p>" +
            "<p><a href=\"" + confirmationLink + "\">Confirm Email</a></p>" +
            "<p>If you did not register for an account, please ignore this email.</p>",
            true // true indicates HTML content
        );

        mailSender.send(message);
        logger.info("Confirmation email sent to: {}", email);
    }
}
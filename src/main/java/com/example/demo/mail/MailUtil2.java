package com.example.demo.mail;

import java.util.Properties;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

public class MailUtil2 {
	public static void sendMail(String recipient) {
		Properties prop = new Properties();
		prop.put("mail.smtp.auth","true");
		prop.put("mail.smtp.starttls.enable","true");
		prop.put("mail.smtp.ssl.trust","smtp.gmail.com");
		prop.put("mail.smtp.host", "smtp.gmail.com");
		prop.put("mail.smtp.port","587");
		String myEmail="";
		String password="";
		Session session = Session.getInstance(prop, new Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(myEmail,password);
			}
		});
		System.out.println("Preparing email to send...");
		Message msg = prepareMessage(session, myEmail, password, recipient);
		try {
			Transport.send(msg);
		} catch (MessagingException e) {
			e.printStackTrace();
		}
		System.out.println("Message successfully sent...");
	}

	private static Message prepareMessage(Session session, String myEmail, String password, String recipient) {
		Message msg = new MimeMessage(session);
		try {
			msg.setFrom(new InternetAddress(myEmail));
			msg.setRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
			msg.setSubject("Request to change password");
			
			msg.setText("http://localhost:8090/resetpasswd/" + recipient);
			return msg;
		} catch (AddressException e) {
			e.printStackTrace();
		} catch (MessagingException e) {
			e.printStackTrace();
		}
		return null;
	}

}

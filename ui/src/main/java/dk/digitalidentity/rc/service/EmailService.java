package dk.digitalidentity.rc.service;

import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j;

@Log4j
@EnableAsync
@Service
public class EmailService {

	@Value("${email.enabled}")
	private boolean emailEnabled;

	@Value("${email.from}")
	private String smtpFrom;

	@Value("${email.username}")
	private String smtpUsername;

	@Value("${email.password}")
	private String smtpPassword;

	@Value("${email.host}")
	private String smtpHost;

	@Async
	public void sendMessage(String email, String subject, String message) {
		if (!emailEnabled) {
			log.warn("email server is not configured - not sending emails!");
			return;
		}

		Transport transport = null;

		try {
			Properties props = System.getProperties();
			props.put("mail.transport.protocol", "smtps");
			props.put("mail.smtp.port", 25);
			props.put("mail.smtp.auth", "true");
			props.put("mail.smtp.starttls.enable", "true");
			props.put("mail.smtp.starttls.required", "true");
			Session session = Session.getDefaultInstance(props);

			MimeMessage msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress(smtpFrom, "OS2rollekatalog"));
			msg.setRecipient(Message.RecipientType.TO, new InternetAddress(email));
			msg.setSubject(subject, "UTF-8");
			msg.setText(message, "UTF-8");
			msg.setHeader("Content-Type", "text/html; charset=UTF-8");

			transport = session.getTransport();
			transport.connect(smtpHost, smtpUsername, smtpPassword);
			transport.addTransportListener(new TransportErrorHandler());
			transport.sendMessage(msg, msg.getAllRecipients());
		}
		catch (Exception ex) {
			log.error("Failed to send email", ex);
		}
		finally {
			try {
				if (transport != null) {
					transport.close();
				}
			}
			catch (Exception ex) {
				log.warn("Error occured while trying to terminate connection", ex);
			}
		}
	}

	@Async
	public void sendMessageWithFileAttached(String email, String subject, String message, byte[] fileData, String fileName) {
		if (!emailEnabled) {
			log.warn("email server is not configured - not sending emails!");
			return;
		}

		Transport transport = null;

		try {
			Properties props = System.getProperties();
			props.put("mail.transport.protocol", "smtps");
			props.put("mail.smtp.port", 25);
			props.put("mail.smtp.auth", "true");
			props.put("mail.smtp.starttls.enable", "true");
			props.put("mail.smtp.starttls.required", "true");
			Session session = Session.getDefaultInstance(props);

			MimeMessage msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress(smtpFrom, "OS2rollekatalog"));
			msg.setRecipient(Message.RecipientType.TO, new InternetAddress(email));
			msg.setSubject(subject, "UTF-8");

			Multipart multipart = new MimeMultipart();

			MimeBodyPart htmlBodyPart = new MimeBodyPart();
			htmlBodyPart.setContent(message, "text/html");

			MimeBodyPart attachmentBodyPart = new MimeBodyPart();
			attachmentBodyPart = new MimeBodyPart();
			DataSource source = new ByteArrayDataSource(fileData, "application/octet-stream");
			attachmentBodyPart.setDataHandler(new DataHandler(source));
			attachmentBodyPart.setFileName(fileName);

			multipart.addBodyPart(htmlBodyPart);
			multipart.addBodyPart(attachmentBodyPart);

			msg.setContent(multipart);

			transport = session.getTransport();
			transport.connect(smtpHost, smtpUsername, smtpPassword);
			transport.addTransportListener(new TransportErrorHandler());
			transport.sendMessage(msg, msg.getAllRecipients());
		}
		catch (Exception ex) {
			log.error("Failed to send email", ex);
		}
		finally {
			try {
				if (transport != null) {
					transport.close();
				}
			}
			catch (Exception ex) {
				log.warn("Error occured while trying to terminate connection", ex);
			}
		}
	}
}

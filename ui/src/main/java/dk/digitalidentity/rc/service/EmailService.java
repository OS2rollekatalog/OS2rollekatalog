package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.viewmodel.InlineImageDTO;
import dk.digitalidentity.rc.dao.model.AttachmentFile;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.PreencodedMimeBodyPart;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Slf4j
@EnableAsync
@Service
public class EmailService {

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Async
	public void sendMessage(String email, String subject, String message, String cc) {
		sendMessage(email, subject, message, new ArrayList<InlineImageDTO>(), cc);
	}
	
	@Async
	public void sendMessage(String email, String subject, String message, List<InlineImageDTO> inlineImages, String cc) {
		if (!configuration.getIntegrations().getEmail().isEnabled()) {
			log.warn("email server is not configured - not sending emails!");
			return;
		}

		if (!StringUtils.hasLength(email)) {
			log.warn("No recipient email given for mail with subject: " + subject);
			return;
		}
		
		log.info("Sending email '" + subject + "' to '" + email + "'" + (StringUtils.hasLength(cc) ? " and CC: '" + cc + "'" : ""));

		Transport transport = null;
		try {
			Properties props = System.getProperties();
			String protocol = configuration.getIntegrations().getEmail().getProtocol();
			props.put("mail.transport.protocol", protocol);
			props.put("mail."+protocol+".port", "" + configuration.getIntegrations().getEmail().getPort());
			props.put("mail."+protocol+".auth", configuration.getIntegrations().getEmail().isAuth() ? "true" : "false");
			props.put("mail."+protocol+".starttls.enable", configuration.getIntegrations().getEmail().isStartTlsEnabled() ? "true" : "false");
			props.put("mail."+protocol+".starttls.required", configuration.getIntegrations().getEmail().isStartTlsRequired() ? "true" : "false");
			Session session = Session.getInstance(props);

			MimeMessage msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress(configuration.getIntegrations().getEmail().getFrom(), "OS2rollekatalog"));
			msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));

			if (StringUtils.hasLength(cc)) {
				msg.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc));
			}

			msg.setSubject(subject, "UTF-8");

			MimeBodyPart messageBodyPart = new MimeBodyPart();
			messageBodyPart.setContent(message, "text/html; charset=UTF-8");

	        Multipart multipart = new MimeMultipart();
	        multipart.addBodyPart(messageBodyPart);

			// adds inline image attachments
	        if (inlineImages != null && inlineImages.size() > 0) {	             
	            for (InlineImageDTO inlineImageDTO : inlineImages) {	                
	                if (inlineImageDTO.isBase64()) {
	                	MimeBodyPart imagePart = new PreencodedMimeBodyPart("base64");
	                	String src = inlineImageDTO.getSrc();
	                	String dataType = org.apache.commons.lang3.StringUtils.substringBetween(src, "data:", ";base64,"); // extract data type ( fx dataType = "image/png")
	                	String base64EncodedFileContent = src.replaceFirst("data:.*;base64,", ""); // remove prefix from fileContent String ( fx base64EncodedFileContent = "iVBORw0KGg......etc"
	                	imagePart.setContent(base64EncodedFileContent, dataType);
	                	imagePart.setFileName(inlineImageDTO.getCid());
	                	imagePart.setHeader("Content-ID", "<" + inlineImageDTO.getCid() + ">");
	                	imagePart.setDisposition(MimeBodyPart.INLINE);
	                	imagePart.setDisposition(Part.ATTACHMENT);

	                	multipart.addBodyPart(imagePart);
	                }
	            }
	        }
	        
			msg.setContent(multipart);
			
			transport = session.getTransport();
			transport.connect(configuration.getIntegrations().getEmail().getHost(),
							  configuration.getIntegrations().getEmail().getUsername(),
							  configuration.getIntegrations().getEmail().getPassword());
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
	public void sendMessageWithAttachments(String email, String subject, String message, List<AttachmentFile> attachments, String cc) {
		sendMessageWithAttachments(email, subject, message, attachments, new ArrayList<InlineImageDTO>(), cc);
	}
	
	@Async
	public void sendMessageWithAttachments(String email, String subject, String message, List<AttachmentFile> attachments, List<InlineImageDTO> inlineImages, String cc) {
		if (!configuration.getIntegrations().getEmail().isEnabled()) {
			log.warn("email server is not configured - not sending emails!");
			return;
		}

		if (!StringUtils.hasLength(email)) {
			log.warn("No recipient email given for mail with subject: " + subject);
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
			msg.setFrom(new InternetAddress(configuration.getIntegrations().getEmail().getFrom(), "OS2rollekatalog"));
			msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
			
			if (StringUtils.hasLength(cc)) {
				msg.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc));
			}
			
			msg.setSubject(subject, "UTF-8");

			MimeBodyPart htmlBodyPart = new MimeBodyPart();
			htmlBodyPart.setContent(message, "text/html; charset=UTF-8");

			Multipart multipart = new MimeMultipart();
			multipart.addBodyPart(htmlBodyPart);

			if (attachments != null) {
				for (AttachmentFile attachment : attachments) {
					MimeBodyPart attachmentBodyPart = new MimeBodyPart();
					attachmentBodyPart = new MimeBodyPart();
					DataSource source = new ByteArrayDataSource(attachment.getContent(), "application/octet-stream");
					attachmentBodyPart.setDataHandler(new DataHandler(source));
					attachmentBodyPart.setFileName(attachment.getFilename());
					
					multipart.addBodyPart(attachmentBodyPart);
				}
			}
			
	        if (inlineImages != null && inlineImages.size() > 0) {	             
	            for (InlineImageDTO inlineImageDTO : inlineImages) {
	                if (inlineImageDTO.isBase64()) {
	                	MimeBodyPart imagePart = new PreencodedMimeBodyPart("base64");
	                	String src = inlineImageDTO.getSrc();
	                	String dataType = org.apache.commons.lang3.StringUtils.substringBetween(src, "data:", ";base64,"); // extract data type ( fx dataType = "image/png")
	                	String base64EncodedFileContent = src.replaceFirst("data:.*;base64,", ""); // remove prefix from fileContent String ( fx base64EncodedFileContent = "iVBORw0KGg......etc"
	                	imagePart.setContent(base64EncodedFileContent, dataType);
	                	imagePart.setFileName(inlineImageDTO.getCid());
	                	imagePart.setHeader("Content-ID", "<" + inlineImageDTO.getCid() + ">");
	                	imagePart.setDisposition(MimeBodyPart.INLINE);
	                	imagePart.setDisposition(Part.ATTACHMENT);
	                	multipart.addBodyPart(imagePart);
	                }
	            }
	        }

			msg.setContent(multipart);

			transport = session.getTransport();
			transport.connect(configuration.getIntegrations().getEmail().getHost(),
					  configuration.getIntegrations().getEmail().getUsername(),
					  configuration.getIntegrations().getEmail().getPassword());
			transport.addTransportListener(new TransportErrorHandler());
			transport.sendMessage(msg, msg.getAllRecipients());
			
			log.info("Sending email '" + subject + "' to '" + email + "'" + (StringUtils.hasLength(cc) ? " and CC: '" + cc + "'" : ""));
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

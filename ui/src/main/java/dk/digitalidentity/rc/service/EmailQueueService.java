package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.controller.mvc.viewmodel.InlineImageDTO;
import dk.digitalidentity.rc.dao.EmailQueueDao;
import dk.digitalidentity.rc.dao.model.AttachmentFile;
import dk.digitalidentity.rc.dao.model.EmailQueue;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class EmailQueueService {
	
	@Autowired
	private EmailQueueDao emailQueueDao;
	
	@Autowired
	private EmailService emailService;

	@Autowired
	private SettingsService settingsService;
	
	public void queueEmail(String email, String title, String message, EmailTemplate template, List<AttachmentFile> attachments, String cc) {
		if (!StringUtils.hasLength(email)) {
			log.info("Not sending email '" + title + "' because no recipient email supplied");
			return;
		}
		
		EmailQueue mail = new EmailQueue();
		mail.setEmail(email);
		mail.setMessage(message);
		mail.setTitle(title);
		mail.setDeliveryTts(new Date());
		mail.setEmailTemplate(template);
		mail.setCc(cc);
		if (attachments != null) {
			mail.addAllAttachments(attachments);
		}
		
		emailQueueDao.save(mail);
	}

	@Transactional
	public void sendPending() {
		List<EmailQueue> emails = findPending();
		
		int limit = settingsService.getEmailQueueLimit();
		if (limit != 0 && (emails.size() > limit)) {
			log.error("Too many emails in the queue. BlockingAllEmailTransmissions.");
			settingsService.setBlockAllEmailTransmissions(true);

			return;
		}
		
		for (EmailQueue email : emails) {
			EmailTemplate template = email.getEmailTemplate();
			
			email.forceLoadAttachments();
			List<AttachmentFile> attachments = (template != null && email.getAttachments() != null && email.getAttachments().size() > 0) ? email.getAttachments() : null;

			if (StringUtils.hasLength(email.getEmail())) {
				if (template != null) {
					List<InlineImageDTO> inlineImages = transformImages(email);

					if (attachments != null) {
						emailService.sendMessageWithAttachments(email.getEmail(), email.getTitle(), email.getMessage(), attachments, inlineImages, email.getCc());
					}
					else {
						emailService.sendMessage(email.getEmail(), email.getTitle(), email.getMessage(), inlineImages, email.getCc());
					}
				}
				else {
					if (attachments != null) {
						emailService.sendMessageWithAttachments(email.getEmail(), email.getTitle(), email.getMessage(), attachments, email.getCc());
					}
					else {
						emailService.sendMessage(email.getEmail(), email.getTitle(), email.getMessage(), email.getCc());
					}
				}
			}
			else {
				log.warn("Cannot send message with title '" + email.getTitle() + "' due to no email");
			}
			
			emailQueueDao.delete(email);
			
			// throttle outgoing emails - we cannot send more than a handful per second through our relay,
			// otherwise we get blocked by the relay
			try {
				Thread.sleep(1000);
			}
			catch (Exception ex) {
				;
			}
		}
	}
	
	private List<EmailQueue> findPending() {
		Date tts = new Date();

		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.SECOND, -5);
		Date fiveSecondsBefore = cal.getTime();
		
		if (emailQueueDao.countByDeliveryTtsAfter(fiveSecondsBefore) > 0) {
			return new ArrayList<EmailQueue>();
		}

		return emailQueueDao.findByDeliveryTtsBefore(tts);
	}
	
	private List<InlineImageDTO> transformImages(EmailQueue email) {
		String message = email.getMessage();
		if (message == null) {
			return Collections.emptyList();
		}
		List<InlineImageDTO> inlineImages = new ArrayList<>();
		Document doc = Jsoup.parse(message);

		for (Element img : doc.select("img")) {
			String src = img.attr("src");
			if (src.isEmpty()) {
				continue;
			}

			InlineImageDTO inlineImageDto = new InlineImageDTO();
			inlineImageDto.setBase64(src.contains("base64"));
			
			if (!inlineImageDto.isBase64()) {
				continue;
			}
			
			String cID = UUID.randomUUID().toString();
			inlineImageDto.setCid(cID);
			inlineImageDto.setSrc(src);
			inlineImages.add(inlineImageDto);
			img.attr("src", "cid:" + cID);
		}

		email.setMessage(doc.html());
		
		return inlineImages;		
	}
}

package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.EmailTemplateDao;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class EmailTemplateService {
	@Autowired
	private EmailTemplateDao emailTemplateDao;
	@Autowired
	private MessageSource messageSource;
	@Autowired
	private RoleCatalogueConfiguration configuration;

	public List<EmailTemplate> findAll() {
		List<EmailTemplate> result = new ArrayList<>();
		
		for (EmailTemplateType type : EmailTemplateType.values()) {
			result.add(findByTemplateType(type));
		}
		
		return result;
	}

	public String getTemplateName(final Long templateId) {
		final EmailTemplate template = emailTemplateDao.findById(templateId)
				.orElseThrow(() -> new RuntimeException("Template not found " + templateId));
		final String title = messageSource.getMessage(template.getTemplateType().getMessage(), null, Locale.ENGLISH);
		if (title.contains("{days_reminder_1}")) {
			return StringUtils.replace(title, "{days_reminder_1}", "" + configuration.getAttestation().getReminder1DaysBeforeDeadline());
		}
		if (title.contains("{days_reminder_2}")) {
			return StringUtils.replace(title, "{days_reminder_2}", "" + configuration.getAttestation().getReminder2DaysBeforeDeadline());
		}
		if (title.contains("{days_reminder_3}")) {
			return StringUtils.replace(title, "{days_reminder_3}", "" + configuration.getAttestation().getReminder3DaysAfterDeadline());
		}
		if (title.contains("{notification_days}")) {
			return StringUtils.replace(title, "{notification_days}", "" + configuration.getAttestation().getNotifyDaysBeforeDeadline());
		}
		if (title.contains("{thirdpart_days}")) {
			return StringUtils.replace(title, "{thirdpart_days}", "" + configuration.getAttestation().getEscalationReminderDaysAfterDeadline());
		}
		return title;
	}

	public EmailTemplate findByTemplateType(EmailTemplateType type) {
		EmailTemplate template = emailTemplateDao.findByTemplateType(type);
		if (template == null) {
			template = new EmailTemplate();
			String title = "Overskrift";
			String message = "Besked";
			boolean enabled = true;
			
			switch (type) {
				case ATTESTATION_REQUEST_FOR_CHANGE:
					title = "Ændrings anmodning";
					message = "Der er modtaget en anmodning fra {anmoder} om følgende ændring: <br/>\n\"{ændring}\"\n<br/>\n<br/>{ændringsønsker}\n<br/>\n<br/>For brugeren {bruger}";
					break;
				case ATTESTATION_REQUEST_FOR_ROLE_CHANGE:
					title = "Ændrings anmodning til rolle";
					message = "Der er modtaget en anmodning fra {anmoder} om ændringer til rollen: {rolle} på it-systemet: {itsystem}";
					break;
				case ATTESTATION_REQUEST_FOR_REMOVAL:
					title = "Slette anmodning";
					message = "Der er modtaget en anmodning fra {anmoder} om sletning af følgende bruger {bruger}";
					break;
				case ATTESTATION_NOTIFICATION:
					title = "Det er tid til attestering";
					message = "Kære {modtager}\n<br/>\n<br/>\nDet er tid til, at der skal attesteres roller for enheden: {enhed}.";
					break;
				case ATTESTATION_REMINDER1:
					title = "Påmindelse/rykker for attestering";
					message = "Kære {modtager}\n<br/>\n<br/>\nDer er ti dage til at der skal være attesteret roller for enheden: {enhed}.";
					break;
				case ATTESTATION_REMINDER2:
					title = "Påmindelse/rykker for attestering";
					message = "Kære {modtager}\n<br/>\n<br/>\nDer er tre dage til at der skal være attesteret roller for enheden: {enhed}.";
					break;
				case ATTESTATION_REMINDER3:
					title = "Påmindelse/rykker for attestering";
					message = "Kære {modtager}\n<br/>\n<br/>\nDet er frem dage siden at der skulle have være attesteret roller for enheden: {enhed}.";
					enabled = false;
					break;
				case ATTESTATION_REMINDER_THIRDPARTY:
					title = "Manglende attestering";
					message = "Kære {modtager}\n<br/>\n<br/>\nDet er tid til, at der skal attesteres roller for enheden: {enhed}. Der er sendt en eller flere rykkere til leder og eventuel stedfortræder, men en attestering er endnu ikke udført.";
					break;
				case ATTESTATION_SENSITIVE_NOTIFICATION:
					title = "Det er tid til attestering af følsomme roller";
					message = "Kære {modtager}\n<br/>\n<br/>\nDet er tid til, at der skal attesteres følsomme roller for enheden: {enhed}.";
					break;
				case ATTESTATION_SENSITIVE_REMINDER1:
					title = "Påmindelse/rykker for attestering af følsomme roller";
					message = "Kære {modtager}\n<br/>\n<br/>\nDer er ti dage til at der skal være attesteret følsomme roller for enheden: {enhed}.";
					break;
				case ATTESTATION_SENSITIVE_REMINDER2:
					title = "Påmindelse/rykker for attestering af følsomme roller";
					message = "Kære {modtager}\n<br/>\n<br/>\nDer er tre dage til at der skal være attesteret følsomme roller for enheden: {enhed}.";
					break;
				case ATTESTATION_SENSITIVE_REMINDER3:
					title = "Påmindelse/rykker for attestering af følsomme roller";
					message = "Kære {modtager}\n<br/>\n<br/>\nDer er tre dage til at der skal være attesteret følsomme roller for enheden: {enhed}.";
					enabled = false;
					break;
				case ATTESTATION_SENSITIVE_REMINDER_THIRDPARTY:
					title = "Manglende attestering af følsomme roller";
					message = "Kære {modtager}\n<br/>\n<br/>\nDet er tid til, at der skal attesteres følsomme roller for enheden: {enhed}. Der er sendt en eller flere rykkere til leder og eventuel stedfortræder, men en attestering er endnu ikke udført.";
					break;
				case ATTESTATION_IT_SYSTEM_NOTIFICATION:
					title = "Det er tid til attestering af rolleopbygning";
					message = "Kære {modtager}\n<br/>\n<br/>\nDet er tid til, at der skal attesteres rolleopbygning for it-systemet: {itsystem}.";
					break;
				case ATTESTATION_IT_SYSTEM_REMINDER1:
					title = "Påmindelse/rykker for attestering af rolleopbygning";
					message = "Kære {modtager}\n<br/>\n<br/>\nDer er ti dage til at der skal være attesteret rolleopbygning for it-systemet: {itsystem}.";
					break;
				case ATTESTATION_IT_SYSTEM_REMINDER2:
					title = "Påmindelse/rykker for attestering af rolleopbygning";
					message = "Kære {modtager}\n<br/>\n<br/>\nDer er tre dage til at der skal være attesteret rolleopbygning for it-systemet: {itsystem}.";
					break;
				case ATTESTATION_IT_SYSTEM_REMINDER3:
					title = "Påmindelse/rykker for attestering af rolleopbygning";
					message = "Kære {modtager}\n<br/>\n<br/>\nDer er tre dage til at der skal være attesteret rolleopbygning for it-systemet: {itsystem}.";
					enabled = false;
					break;
				case ATTESTATION_IT_SYSTEM_REMINDER_THIRDPARTY:
					title = "Manglende attestering af rolleopbygning";
					message = "Kære {modtager}\n<br/>\n<br/>\nDet er tid til, at der skal attesteres rolleopbygning for it-systemet: {itsystem}. Der er sendt en eller flere rykkere til leder og eventuel stedfortræder, men en attestering er endnu ikke udført.";
					break;
					case SUBSTITUTE:
					title = "Du er blevet udpeget som stedfortræder";
					message = "Kære {modtager}\n<br/>\n<br/>\nDu er blevet udpeget til stedfortræder for {leder} for enheden {enhed}.";
					break;
				case ROLE_EXPIRING:
					title = "Tidsbegrænsede rettigheder er ved at udløbe";
					message = "Kære {modtager}\n<br/>\n<br/>\nEn eller flere tidsbegrænsede rettigheder udløber inden for 14 dage. De kan ses af den vedhæftede pdf.";
					break;
				case APPROVED_ROLE_REQUEST_USER:
					title = "Du har fået tildelt en rolle";
					message = "Kære {modtager}\n<br/>\n<br/>\nEn autorisationsansvarlig eller leder har anmodet om rollen {rolle} til dig. Den er nu tildelt.";
					break;
				case APPROVED_ROLE_REQUEST_REMOVAL_USER:
					title = "Du har fået fjernet en rolle";
					message = "Kære {modtager}\n<br/>\n<br/>\nEn autorisationsansvarlig eller leder har anmodet om at få rollen {rolle} fjernet. Den er nu fjernet.";
					break;
				case APPROVED_ROLE_REQUEST_MANAGER:
					title = "En anmodning om en rolle er godkendt";
					message = "Kære {modtager}\n<br/>\n<br/>\nRollen {rolle} {anmoder} har anmodet om til {bruger}, er nu {operation}.";
					break;
				case REJECTED_ROLE_REQUEST_MANAGER:
					title = "En anmodning om en rolle er afvist";
					message = "Kære {modtager}\n<br/>\n<br/>\nRollen {rolle} {anmoder} har bedt om at få {operation} for {bruger}, er blevet afvist.";
					break;
				case WAITING_REQUESTS_ROLE_ASSIGNERS:
					title = "Der er afventende rolleanmodninger";
					message = "Kære {modtager}\n<br/>\n<br/>\nDer er {antal} rolleanmodning(er), der skal tages stilling til.";
					break;
				case APPROVED_MANUAL_ROLE_REQUEST_USER:
					title = "Du har fået tildelt en rolle";
					message = "Kære {modtager}\n<br/>\n<br/>\nEn autorisationsansvarlig eller leder har anmodet om rollen {rolle} til dig. Den er nu tildelt.";
					break;
				case APPROVED_MANUAL_ROLE_REQUEST_MANAGER:
					title = "En anmodning om en rolle er godkendt";
					message = "Kære {modtager}\n<br/>\n<br/>\nRollen {rolle} {anmoder} har bedt om at få {operation} for {bruger}, er nu godkendt.";
					break;
				case USER_WITH_MANUAL_ITSYSTEM_DELETED:
					title = "En bruger med manuelle roller er blevet nedlagt";
					message = "Til den ansvarlige for {itsystem}\n<\br>\nBrugeren {bruger} er blevet nedlagt, og denne bruger har adgange i {itsystem}, som muligvis kræver manuel behandling";
					break;
			}
			
			template.setTitle(title);
			template.setMessage(message);
			template.setTemplateType(type);
			template.setEnabled(enabled);
			
			template = emailTemplateDao.save(template);
		}
		
		return template;
	}

	public EmailTemplate save(EmailTemplate template) {
		return emailTemplateDao.save(template);
	}
	
	public EmailTemplate findById(long id) {
		return emailTemplateDao.findById(id).orElse(null);
	}
}

package dk.digitalidentity.rc.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.EmailTemplateDao;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;

@Service
public class EmailTemplateService {
	public static final String RECEIVER_PLACEHOLDER = "{modtager}";
	public static final String ORGUNIT_PLACEHOLDER = "{enhed}";
	
	@Autowired
	private EmailTemplateDao emailTemplateDao;

	public List<EmailTemplate> findAll() {
		List<EmailTemplate> result = new ArrayList<>();
		
		for (EmailTemplateType type : EmailTemplateType.values()) {
			result.add(findByTemplateType(type));
		}
		
		return result;
	}

	public EmailTemplate findByTemplateType(EmailTemplateType type) {
		EmailTemplate template = emailTemplateDao.findByTemplateType(type);
		if (template == null) {
			template = new EmailTemplate();
			String title = "Overskrift";
			String message = "Besked";
			
			switch (type) {
				case REMOVE_UNIT_ROLES:
					title = "Nedarvede roller til sletning";
					message = "Kære {modtager}\n<br/>\n<br/>\nDer er et ønske om, at nogle nedarvede roller skal fjernes. De berørte roller kan ses af den vedhæftede pdf.";
					break;
				case ATTESTATION_DOCUMENTATION:
					title = "Attesteringsrapport";
					message = "Kære {modtager}\n<br/>\n<br/>\nDer er vedhæftet en attesteringsrapport til denne mail, hvor ændringer for enheden, der er attesteret kan ses.";
					break;
				case ATTESTATION_NOTIFICATION:
					title = "Det er tid til attestering";
					message = "Kære {modtager}\n<br/>\n<br/>\nDet er tid til, at der skal attesteres roller for enheden: {enhed}.";
					break;
				case ATTESTATION_REMINDER:
					title = "Rykker for attestering";
					message = "Kære {modtager}\n<br/>\n<br/>\nDet er tid til, at der skal attesteres roller for enheden: {enhed}.";
					break;
				case ATTESTATION_REMINDER_THIRDPARTY:
					title = "Rykker for attestering";
					message = "Kære {modtager}\n<br/>\n<br/>\nDet er tid til, at der skal attesteres roller for enheden: {enhed}. Der er sendt en eller flere rykkere til leder og eventuel stedfortræder, men en attesteering er endnu ikke udført.";
					break;
				case SUBSTITUTE:
					title = "Du er blevet udpeget som stedfortræder";
					message = "Kære {modtager}\n<br/>\n<br/>\nDu er blevet udpeget til stedfortræder.";
					break;
				case ATTESTATION_EMPLOYEE_NEW_UNIT:
					title = "En medarbejder har skiftet enhed";
					message = "Kære {modtager}\n<br/>\n<br/>\nEn medarbejder har skiftet enhed, og der skal derfor attesteres.";
					break;
				case ROLE_EXPIRING:
					title = "Tidsbegrænsede rettigheder er ved at udløbe";
					message = "Kære {modtager}\n<br/>\n<br/>\nEn eller flere tidsbegrænsede rettigheder udløber inden for 14 dage. De kan ses af den vedhæftede pdf.";
					break;
			}
			
			template.setTitle(title);
			template.setMessage(message);
			template.setTemplateType(type);
			
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

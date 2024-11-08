package dk.digitalidentity.rc.service;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import dk.digitalidentity.rc.dao.model.AttachmentFile;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.PositionRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.PositionUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RoleNotificationService {

	@Autowired
	private OrgUnitService orgUnitService;
	
	@Autowired
	private PositionService positionService;
	
	@Autowired
	private TemplateEngine templateEngine;
	
	@Autowired
	private EmailQueueService emailQueueService;
	
	@Autowired
	private EmailTemplateService emailTemplateService;

	@Autowired
	private ManagerSubstituteService managerSubstituteService;

	@Transactional
	public void notifyAboutExpiringRoles() throws Exception {
		List<OrgUnit> ous = orgUnitService.getAll();
		LocalDate modifiedDate = LocalDate.now().plusDays(14);

		for (OrgUnit ou : ous) {
			List<String> expiringStrings = new ArrayList<>();
			
			User manager = ou.getManager();
			if (manager == null) {
				continue;
			}
			
			String managerEmail = null;
			if (StringUtils.hasLength(manager.getEmail())) {
				managerEmail = manager.getEmail();
			}

			List<User> substitutes = new ArrayList<>();
			
			substitutes.addAll(managerSubstituteService.getSubstitutesForOrgUnit(ou).stream().filter(u -> !u.isDeleted() && StringUtils.hasLength(u.getEmail())).toList());

			if (managerEmail == null && substitutes.isEmpty()) {
				continue;
			}

			// finding assignments with stopDate within 14 days for orgUnit
			for (OrgUnitRoleGroupAssignment ourga : ou.getRoleGroupAssignments()) {
				if (ourga.getStopDate() != null) {
					if (ourga.getStopDate().isBefore(modifiedDate)) {
						String ourgaString = "Rollebuketten " + ourga.getRoleGroup().getName() + " tildelt på enheden " + ou.getName() + " udløber " + ourga.getStopDate();
						expiringStrings.add(ourgaString);
					}
				}
			}
			
			for (OrgUnitUserRoleAssignment ouura : ou.getUserRoleAssignments()) {
				if (ouura.getStopDate() != null) {
					if (ouura.getStopDate().isBefore(modifiedDate)) {
						String ouuraString = "Jobfunktionsrollen " + ouura.getUserRole().getName() + " (" + ouura.getUserRole().getItSystem().getName() + ") tildelt på enheden " + ou.getName() + " udløber " + ouura.getStopDate();
						expiringStrings.add(ouuraString);
					}
				}
			}
			
			// finding assignments with stopDate within 14 days for titles associated with the OU
			List<OrgUnitUserRoleAssignment> userRoleAssigments = ou.getUserRoleAssignments().stream().filter(ura->ura.getContainsTitles() != ContainsTitles.NO).collect(Collectors.toList());
			for (OrgUnitUserRoleAssignment oura : userRoleAssigments) {
				if (oura.getStopDate() != null && oura.getStopDate().isBefore(modifiedDate)) {
					for (Title title : oura.getTitles()) {
						String turaString = "Jobfunktionsrollen " + oura.getUserRole().getName() + " (" + oura.getUserRole().getItSystem().getName() + ") tildelt på titlen " + title.getName() + " associeret med enheden " + ou.getName() +  " udløber " + oura.getStopDate();
						expiringStrings.add(turaString);
					}
				}
			}
			
			List<OrgUnitRoleGroupAssignment> roleGroupAssigments = ou.getRoleGroupAssignments().stream().filter(rga->rga.getContainsTitles() != ContainsTitles.NO).collect(Collectors.toList());
			for (OrgUnitRoleGroupAssignment orga : roleGroupAssigments) {
				if (orga.getStopDate() != null && orga.getStopDate().isBefore(modifiedDate)) {
					for (Title title : orga.getTitles()) {
						String trgaString = "Rollebuketten " + orga.getRoleGroup().getName() + " tildelt på titlen " + title.getName() + " associeret med enheden " + ou.getName() +  " udløber " + orga.getStopDate();
						expiringStrings.add(trgaString);
					}
				}
			}
			
			// finding assignments with stopDate within 14 days for positions associated with the OU
			List<Position> positions = positionService.findByOrgUnit(ou);
			for (Position position : positions) {
				User user = position.getUser();
				if (user == null || user.isDeleted()) {
					continue;
				}
				
				for (PositionRoleGroupAssignment prga : position.getRoleGroupAssignments()) {
					if (prga.getStopDate() != null) {
						if (prga.getStopDate().isBefore(modifiedDate)) {
							String prgaString = "Rollebuketten " + prga.getRoleGroup().getName() + " tildelt brugeren " + user.getName() + " udløber " + prga.getStopDate();
							expiringStrings.add(prgaString);
						}
					}
				}
				
				for (PositionUserRoleAssignment pura : position.getUserRoleAssignments()) {
					if (pura.getStopDate() != null) {
						if (pura.getStopDate().isBefore(modifiedDate)) {
							String puraString = "Jobfunktionsrollen " + pura.getUserRole().getName() + " (" + pura.getUserRole().getItSystem().getName() + ") tildelt brugeren " + user.getName() + " udløber " + pura.getStopDate();
							expiringStrings.add(puraString);
						}
					}
				}
				
				// finding assignments with stopDate within 14 days for users associated with the position
				for (UserUserRoleAssignment uura : user.getUserRoleAssignments()) {
					if (uura.getStopDate() != null) {
						if (uura.getStopDate().isBefore(modifiedDate)) {
							String uuraString = "Jobfunktionsrollen " + uura.getUserRole().getName() + " (" + uura.getUserRole().getItSystem().getName() + ") tildelt brugeren " + user.getName() + " udløber " + uura.getStopDate();
							expiringStrings.add(uuraString);
						}
					}
				}
				
				for (UserRoleGroupAssignment urga : user.getRoleGroupAssignments()) {
					if (urga.getStopDate() != null) {
						if (urga.getStopDate().isBefore(modifiedDate)) {
							String urgaString = "Rollebuketten " + urga.getRoleGroup().getName() + " tildelt brugeren " + user.getName() + " udløber " + urga.getStopDate();
							expiringStrings.add(urgaString);
						}
					}
				}
			}
			
			// Send mail to manager/substitute with expiring roles
			if (!expiringStrings.isEmpty()) {
				Context ctx = new Context();
				ctx.setVariable("strings", expiringStrings);
				ctx.setVariable("ou", ou.getName());

				SimpleDateFormat simpleDateFormatConfirmation = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				ctx.setVariable("time", simpleDateFormatConfirmation.format(new Date()));

				String htmlContentConfirmation = templateEngine.process("manager/expiring_roles_pdf", ctx);
				
				// Create PDF document
				byte[] pdf = null;
				try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
					ITextRenderer renderer = new ITextRenderer();
					renderer.setDocumentFromString(htmlContentConfirmation);
					renderer.layout();
					renderer.createPDF(outputStream);

					pdf = outputStream.toByteArray();
				} finally {
					;
				}
				
				if (pdf != null) {
					if (!substitutes.isEmpty()) {
						EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.ROLE_EXPIRING);
						
						if (template.isEnabled()) {
							for (User substitute : substitutes) {
								AttachmentFile attachmentFile = new AttachmentFile();
								attachmentFile.setContent(pdf);
								attachmentFile.setFilename("Rettighedsudløb.pdf");
								List<AttachmentFile> attachments = new ArrayList<>();
								attachments.add(attachmentFile);
								
								String title = template.getTitle();
								title = title.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), substitute.getName());
								title = title.replace(EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER.getPlaceholder(), ou.getName());
								String message = template.getMessage();
								message = message.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), substitute.getName());
								message = message.replace(EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER.getPlaceholder(), ou.getName());
								emailQueueService.queueEmail(substitute.getEmail(), title, message, template, attachments, null);
							}
						}
						else {
							log.info("Email template with type " + template.getTemplateType() + " is disabled. Email was not sent.");
						}
					}
					
					if (managerEmail != null) {
						EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.ROLE_EXPIRING);
						
						if (template.isEnabled()) {
							AttachmentFile attachmentFile = new AttachmentFile();
							attachmentFile.setContent(pdf);
							attachmentFile.setFilename("Rettighedsudløb.pdf");
							List<AttachmentFile> attachments = new ArrayList<>();
							attachments.add(attachmentFile);
							String title = template.getTitle();
							title = title.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), manager.getName());
							title = title.replace(EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER.getPlaceholder(), ou.getName());
							String message = template.getMessage();
							message = message.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), manager.getName());
							message = message.replace(EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER.getPlaceholder(), ou.getName());
							emailQueueService.queueEmail(managerEmail, title, message, template, attachments, null);
						}
						else {
							log.info("Email template with type " + template.getTemplateType() + " is disabled. Email was not sent.");
						}
					}
				}
			}
		}
	}

}

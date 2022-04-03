package dk.digitalidentity.rc.service;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryRoleAssignment;
import dk.digitalidentity.rc.dao.model.AttachmentFile;
import dk.digitalidentity.rc.dao.model.AttestationNotification;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.PositionRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.PositionUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.enums.CheckupIntervalEnum;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AttestationService {

	@Autowired
	private AttestationNotificationService attestationNotificationService;

	@Autowired 
	private SettingsService settingsService;

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
	private UserRoleService userRoleService;
	
	@Autowired
	private HistoryService historyService;

	public void flagOrgUnitForImmediateAttesation(OrgUnit orgUnit) {
		if (!settingsService.isScheduledAttestationEnabled()) {
			return;
		}

		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, settingsService.getDaysBeforeDeadline());
		
		orgUnit.setNextAttestation(cal.getTime());
		orgUnitService.save(orgUnit);
	}

	@Transactional
	public void notifyAboutExpiringRoles() throws Exception {
		// we need SOME way to disable this, and this is the best we have for now
		if (!settingsService.isScheduledAttestationEnabled()) {
			return;
		}
		
		List<OrgUnit> ous = orgUnitService.getAll();
		LocalDate modifiedDate = LocalDate.now().plusDays(14);

		Set<String> filter = settingsService.getScheduledAttestationFilter();
		
		for (OrgUnit ou : ous) {
			boolean attestationEnabled = attestationEnabled(filter, ou);
			if (!attestationEnabled) {
				continue;
			}

			List<String> expiringStrings = new ArrayList<>();
			
			User manager = ou.getManager();
			if (manager == null) {
				continue;
			}
			
			String managerEmail = null;
			if (!StringUtils.isEmpty(manager.getEmail())) {
				managerEmail = manager.getEmail();
			}

			String substituteEmail = null;			
			User substitute = manager.getManagerSubstitute();
			if (substitute != null && substitute.isActive() && !StringUtils.isEmpty(substitute.getEmail())) {
				substituteEmail = substitute.getEmail();
			}
			
			if (managerEmail == null && substituteEmail == null) {
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
						String ouuraString = "Jobfunktionsrollen " + ouura.getUserRole().getName() + " tildelt på enheden " + ou.getName() + " udløber " + ouura.getStopDate();
						expiringStrings.add(ouuraString);
					}
				}
			}
			
			// finding assignments with stopDate within 14 days for titles associated with the OU
			List<OrgUnitUserRoleAssignment> userRoleAssigments = ou.getUserRoleAssignments().stream().filter(ura->ura.isContainsTitles()).collect(Collectors.toList());
			for (OrgUnitUserRoleAssignment oura : userRoleAssigments) {
				if (oura.getStopDate() != null && oura.getStopDate().isBefore(modifiedDate)) {
					for (Title title : oura.getTitles()) {
						String turaString = "Jobfunktionsrollen " + oura.getUserRole().getName() + " tildelt på titlen " + title.getName() + " associeret med enheden " + ou.getName() +  " udløber " + oura.getStopDate();
						expiringStrings.add(turaString);
					}
				}
			}
			
			List<OrgUnitRoleGroupAssignment> roleGroupAssigments = ou.getRoleGroupAssignments().stream().filter(rga->rga.isContainsTitles()).collect(Collectors.toList());
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
				if (user == null || !user.isActive()) {
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
							String puraString = "Jobfunktionsrollen " + pura.getUserRole().getName() + " tildelt brugeren " + user.getName() + " udløber " + pura.getStopDate();
							expiringStrings.add(puraString);
						}
					}
				}
				
				// finding assignments with stopDate within 14 days for users associated with the position
				for (UserUserRoleAssignment uura : user.getUserRoleAssignments()) {
					if (uura.getStopDate() != null) {
						if (uura.getStopDate().isBefore(modifiedDate)) {
							String uuraString = "Jobfunktionsrollen " + uura.getUserRole().getName() + " tildelt brugeren " + user.getName() + " udløber " + uura.getStopDate();
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
					if (substituteEmail != null) {
						EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.ROLE_EXPIRING);
						if (template.isEnabled()) {
							AttachmentFile attachmentFile = new AttachmentFile();
							attachmentFile.setContent(pdf);
							attachmentFile.setFilename("Rettighedsudløb.pdf");
							List<AttachmentFile> attachments = new ArrayList<>();
							attachments.add(attachmentFile);
							String title = template.getTitle();
							title = title.replace(EmailTemplateService.RECEIVER_PLACEHOLDER, substitute.getName());
							title = title.replace(EmailTemplateService.ORGUNIT_PLACEHOLDER, ou.getName());
							String message = template.getMessage();
							message = message.replace(EmailTemplateService.RECEIVER_PLACEHOLDER, substitute.getName());
							message = message.replace(EmailTemplateService.ORGUNIT_PLACEHOLDER, ou.getName());
							emailQueueService.queueEmail(substituteEmail, title, message, template, attachments);
						} else {
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
							title = title.replace(EmailTemplateService.RECEIVER_PLACEHOLDER, manager.getName());
							title = title.replace(EmailTemplateService.ORGUNIT_PLACEHOLDER, ou.getName());
							String message = template.getMessage();
							message = message.replace(EmailTemplateService.RECEIVER_PLACEHOLDER, manager.getName());
							message = message.replace(EmailTemplateService.ORGUNIT_PLACEHOLDER, ou.getName());
							emailQueueService.queueEmail(managerEmail, title, message, template, attachments);
						} else {
							log.info("Email template with type " + template.getTemplateType() + " is disabled. Email was not sent.");
						}
					}
				}
			}
		}
	}

	@Transactional
	public void firstNotify() {		
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, settingsService.getDaysBeforeDeadline());
		Date triggerDate = cal.getTime();
		
		List<OrgUnit> orgUnits = orgUnitService.getAll();
		for (OrgUnit ou : orgUnits) {

			// make sure we have someone to send emails to (preferSubstitutes, so we will only ever get one, the loop below is silly)
			Map<String, String> emails = OrgUnitService.getManagerAndSubstituteEmail(ou, true);
			if (emails.size() == 0) {
				continue;
			}
			
			// no planned attestation, or not reached deadline yet
			if (ou.getNextAttestation() == null || !ou.getNextAttestation().before(triggerDate)) {
				continue;
			}

			List<AttestationNotification> attestationNotifications = attestationNotificationService.getByOrgUnit(ou);
			if (attestationNotifications.isEmpty()) {
				for (String email : emails.keySet()) {
					EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_NOTIFICATION);
					if (template.isEnabled()) {
						String title = template.getTitle();
						title = title.replace(EmailTemplateService.RECEIVER_PLACEHOLDER, emails.get(email));
						title = title.replace(EmailTemplateService.ORGUNIT_PLACEHOLDER, ou.getName());
						
						String message = template.getMessage();
						message = message.replace(EmailTemplateService.RECEIVER_PLACEHOLDER, emails.get(email));
						message = message.replace(EmailTemplateService.ORGUNIT_PLACEHOLDER, ou.getName());
						emailQueueService.queueEmail(email, title, message, template, null);

						// what does this table keep track of?
						AttestationNotification attestationNotification = new AttestationNotification();
						attestationNotification.setOrgUnit(ou);
						attestationNotification.setTimestamp(new Date());
						attestationNotificationService.save(attestationNotification);
					} else {
						log.info("Email template with type " + template.getTemplateType() + " is disabled. Email was not sent.");
					}
				}
			}
		}
	}

	@Transactional
	public void notifyReminder() {
		int reminderCount = settingsService.getReminderCount();
		List<OrgUnit> orgUnits = orgUnitService.getAll();
		Date now = new Date();
		
		for (OrgUnit ou : orgUnits) {
			
			// make sure we have someone to send emails to (do not prefer substitutes, so get both email adresses if available)
			Map<String, String> emails = OrgUnitService.getManagerAndSubstituteEmail(ou, false);
			if (emails.size() == 0) {
				continue;
			}

			List<AttestationNotification> attestationNotifications = attestationNotificationService.getByOrgUnit(ou);

			// we have the original notification in the table also, so <= is the correct check
			if (attestationNotifications.size() > 0 && attestationNotifications.size() <= reminderCount) {
				attestationNotifications.sort(Comparator.comparing(AttestationNotification::getTimestamp));
				AttestationNotification newestNotification = attestationNotifications.get(attestationNotifications.size() - 1);
				
				if (ou.getNextAttestation() != null) {
					Calendar cal = Calendar.getInstance();
					cal.setTime(newestNotification.getTimestamp());
					cal.add(Calendar.DATE, settingsService.getReminderInterval());
					Date modifiedDate = cal.getTime();

					if (modifiedDate.before(now)) {
						EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REMINDER);
						if (template.isEnabled()) {
							for (String email : emails.keySet()) {
								EmailTemplate tempTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REMINDER);
								
								String title = tempTemplate.getTitle();
								title = title.replace(EmailTemplateService.RECEIVER_PLACEHOLDER, emails.get(email));
								title = title.replace(EmailTemplateService.ORGUNIT_PLACEHOLDER, ou.getName());
								
								String message = tempTemplate.getMessage();
								message = message.replace(EmailTemplateService.RECEIVER_PLACEHOLDER, emails.get(email));
								message = message.replace(EmailTemplateService.ORGUNIT_PLACEHOLDER, ou.getName());
								emailQueueService.queueEmail(email, title, message, tempTemplate, null);
							}
							
							// add a notification to the log (we use it above to keep track of how mamy reminders to send)
							AttestationNotification attestationNotification = new AttestationNotification();
							attestationNotification.setOrgUnit(ou);
							attestationNotification.setTimestamp(new Date());
							
							attestationNotificationService.save(attestationNotification);
						} else {
							log.info("Email template with type " + template.getTemplateType() + " is disabled. Emails were not sent.");
						}
					}
				}
			}
		}
	}

	@Transactional
	public void notifyThirdParty() {
		String mail = settingsService.getEmailAfterReminders();
		if (StringUtils.isEmpty(mail)) {
			return;
		}
		
		int reminderCount = settingsService.getReminderCount();
		List<OrgUnit> orgUnits = orgUnitService.getAll();
		Date now = new Date();
		
		for (OrgUnit ou : orgUnits) {
			List<AttestationNotification> attestationNotifications = attestationNotificationService.getByOrgUnit(ou);
			
			if (attestationNotifications.size() == (reminderCount + 1)) {
				attestationNotifications.sort(Comparator.comparing(AttestationNotification::getTimestamp));
				AttestationNotification newestNotification = attestationNotifications.get(attestationNotifications.size() - 1);
				
				if (ou.getNextAttestation() != null) {					
					Calendar cal = Calendar.getInstance();
					cal.setTime(newestNotification.getTimestamp());
					cal.add(Calendar.DATE, settingsService.getReminderInterval());
					Date modifiedDate = cal.getTime();

					if (modifiedDate.before(now)) {						
						EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REMINDER_THIRDPARTY);
						if (template.isEnabled()) {
							String title = template.getTitle();
							title = title.replace(EmailTemplateService.RECEIVER_PLACEHOLDER, mail);
							title = title.replace(EmailTemplateService.ORGUNIT_PLACEHOLDER, ou.getName());
							
							String message = template.getMessage();
							message = message.replace(EmailTemplateService.RECEIVER_PLACEHOLDER, mail);
							message = message.replace(EmailTemplateService.ORGUNIT_PLACEHOLDER, ou.getName());
							emailQueueService.queueEmail(mail, title, message, template, null);
	
							// make sure we only send this ONE extra notification
							AttestationNotification attestationNotification = new AttestationNotification();
							attestationNotification.setOrgUnit(ou);
							attestationNotification.setTimestamp(new Date());
							
							attestationNotificationService.save(attestationNotification);
						} else {
							log.info("Email template with type " + template.getTemplateType() + " is disabled. Email was not sent.");
						}
					}
				}
			}
		}
	}

	public boolean isSensitive(OrgUnit orgUnit) {

		// need some sensitive UserRoles for this to return true
		List<UserRole> userRoles = userRoleService.getAllSensitiveRoles();
		if (userRoles == null || userRoles.size() == 0) {
			return false;
		}

		Map<String, List<HistoryOURoleAssignment>> ouRoleAssignments = historyService.getOURoleAssignments(LocalDate.now());
		Map<String, List<HistoryRoleAssignment>> userRoleAssignments = historyService.getRoleAssignments(LocalDate.now());

		return isSensitive(orgUnit, userRoles, ouRoleAssignments.get(orgUnit.getUuid()), userRoleAssignments);
	}

	public boolean isSensitive(OrgUnit orgUnit, List<UserRole> userRoles, List<HistoryOURoleAssignment> ouRoleAssignments, Map<String, List<HistoryRoleAssignment>> userRoleAssignments) {

		// if we get empty results, history generation has not run yet
		if ((ouRoleAssignments == null || ouRoleAssignments.size() == 0) && (userRoleAssignments == null || userRoleAssignments.size() == 0)) {
			log.warn("History generation not available for OU: " + orgUnit.getUuid());
			return false;
		}

		if (ouRoleAssignments != null && ouRoleAssignments.size() > 0) {
			for (HistoryOURoleAssignment assignment : ouRoleAssignments) {
				if (userRoles.stream().anyMatch(ur -> ur.getId() == assignment.getRoleId())) {

					return true;
				}
			}
		}

		List<String> userUuids = positionService.findUserUuidByOrgUnitAndActiveUsers(orgUnit);
		if (userUuids.size() > 0) {
			for (String uuid : userRoleAssignments.keySet()) {
				if (userUuids.stream().anyMatch(u -> Objects.equals(uuid, u))) {
					List<HistoryRoleAssignment> assignments = userRoleAssignments.get(uuid);
					
					for (HistoryRoleAssignment assignment : assignments) {
						if (userRoles.stream().anyMatch(ur -> ur.getId() == assignment.getRoleId())) {
							return true;
						}
					}
				}
			}
		}
		
		return false;
	}

	@Transactional
	public void setNextAttestationDeadlines() {
		List<OrgUnit> orgUnits = orgUnitService.getAll();
		Set<String> filter = settingsService.getScheduledAttestationFilter();
		int daysBeforeDeadline = settingsService.getDaysBeforeDeadline();

		// optimize lookup for sensitive roles (once per run, not once per OU)
		Map<String, List<HistoryOURoleAssignment>> ouRoleAssignments = new HashMap<>();
		Map<String, List<HistoryRoleAssignment>> userRoleAssignments = new HashMap<>();

		List<UserRole> userRoles = userRoleService.getAllSensitiveRoles();
		
		if (userRoles != null && userRoles.size() > 0) {
			ouRoleAssignments = historyService.getOURoleAssignments(LocalDate.now());
			userRoleAssignments = historyService.getRoleAssignments(LocalDate.now());
		}
		
		Date nextAttestationDateOrdinary = getNextAttestationDate(LocalDate.now(), false);
		Date nextAttestationDateSensitive = getNextAttestationDate(LocalDate.now(), true);
		
		// we also need these, to deal with attestations performed BEFORE the next deadline, but AFTER the first reminder has been set
		Date nextNextAttestationDateOrdinary = getNextAttestationDate(Instant.ofEpochMilli(nextAttestationDateOrdinary.getTime()).atZone(ZoneId.systemDefault()).toLocalDate(), false);
		Date nextNextAttestationDateSensitive = getNextAttestationDate(Instant.ofEpochMilli(nextAttestationDateSensitive.getTime()).atZone(ZoneId.systemDefault()).toLocalDate(), false);

		boolean adAttestationEnabled = settingsService.isADAttestationEnabled();
		
		for (OrgUnit orgUnit : orgUnits) {
			boolean attestationEnabled = attestationEnabled(filter, orgUnit);
			
			if (!attestationEnabled) {
				if (orgUnit.getNextAttestation() != null) {
					orgUnit.setNextAttestation(null);
					orgUnitService.save(orgUnit);					
				}
			}
			else {
				Set<User> users = positionService.findByOrgUnit(orgUnit).stream().map(p -> p.getUser()).collect(Collectors.toSet());
				
				if (adAttestationEnabled) {
					// we skip OrgUnits where there are no users
					if (users.isEmpty()) {
						orgUnit.setNextAttestation(null);
						orgUnitService.save(orgUnit);
						continue;
					}					
				}
				else {
					long usersWithAssignments = users.stream().filter(u -> !u.getUserRoleAssignments().isEmpty() || !u.getRoleGroupAssignments().isEmpty()).count();

					// we skip OrgUnits where there are no assignments (or no users, because then... well...)
					if (users.isEmpty() || (orgUnit.getUserRoleAssignments().isEmpty() && orgUnit.getRoleGroupAssignments().isEmpty() && usersWithAssignments == 0)) {
						orgUnit.setNextAttestation(null);
						orgUnitService.save(orgUnit);
						continue;					
					}
				}
				
				boolean sensitive = isSensitive(orgUnit, userRoles, ouRoleAssignments.get(orgUnit.getUuid()), userRoleAssignments);
				
				// default values - may be overwritten by the check below
				Date nextAttestationDate = (sensitive) ? nextAttestationDateSensitive : nextAttestationDateOrdinary;

				LocalDate lastAttestationDate = (orgUnit.getLastAttested() != null) ? convertToLocalDate(orgUnit.getLastAttested()) : LocalDate.of(1970, 1, 1);
				LocalDate cutpoint = convertToLocalDate(nextAttestationDate).minusDays(daysBeforeDeadline);

				if (cutpoint.isAfter(lastAttestationDate) && orgUnit.getNextAttestation() != null) {
					// we have an overdue attestation, so we should not update
					// Or an attestation is fast forwarded, so we should not update
					continue;
				}
				else if (cutpoint.isBefore(lastAttestationDate) || cutpoint.isEqual(lastAttestationDate)) {
					// we have a completed attestation, before the deadline, so move to nextNext attestation deadline
					nextAttestationDate = (sensitive) ? nextNextAttestationDateSensitive : nextNextAttestationDateOrdinary;
				}
				

				// super-safe date-only comparison
				if (orgUnit.getNextAttestation() == null || !(convertToLocalDate(orgUnit.getNextAttestation())).equals(convertToLocalDate(nextAttestationDate))) {
					orgUnit.setNextAttestation(nextAttestationDate);
					orgUnitService.save(orgUnit);
				}
			}
		}
	}
	
	public Date getNextAttestationDate(LocalDate afterThisTts, boolean sensitive) {
		int month = afterThisTts.getMonthValue();
		int day = afterThisTts.getDayOfMonth();
		int year = afterThisTts.getYear();

		int dayInMonth = (int) settingsService.getScheduledAttestationDayInMonth();
		CheckupIntervalEnum interval;
		if (sensitive) {
			interval = settingsService.getScheduledAttestationIntervalSensitive();
		}
		else {
			interval = settingsService.getScheduledAttestationInterval();
		}

		switch (interval) {
			case MONTHLY:
				if (day > dayInMonth) {
					month = month + 1;
					if (month > 12) {
						month = 1;
						year = year + 1;
					}
				}

				break;
			case QUARTERLY:
				if (month < 3 || (month == 3 && day < dayInMonth)) {
					month = 3;
				}
				else if (month < 6 || (month == 6 && day < dayInMonth)) {
					month = 6;
				}
				else if (month < 9 || month == 9 && day < dayInMonth) {
					month = 9;
				}
				else if (month < 12 || (month == 12 && day < dayInMonth)) {
					month = 12;
				}
				else {
					month = 3;
					year = year + 1;
				}
				
				break;
			case EVERY_HALF_YEAR:
				if (month < 3 || (month == 3 && day < dayInMonth)) {
					month = 3;
				}
				else if (month < 9 || (month == 9 && day < dayInMonth)) {
					month = 9;
				}
				else {
					month = 3;
					year = year + 1;
				}

				break;
			case YEARLY:
				if (month < 3 || (month == 3 && day < dayInMonth)) {
					month = 3;
				}
				else {
					month = 3;
					year = year + 1;
				}

				break;
		}

		Calendar cal = Calendar.getInstance();
		cal.set(year, (month - 1), dayInMonth, 3, 0, 0);
		
		return cal.getTime();
	}

	private boolean attestationEnabled(Set<String> filter, OrgUnit orgUnit) {
		if (orgUnit == null || filter == null || filter.size() == 0) {
			return false;
		}
		
		if (filter.contains(orgUnit.getUuid())) {
			return true;
		}
		
		return attestationEnabled(filter, orgUnit.getParent());
	}
	
    private LocalDate convertToLocalDate(Date dateToConvert) {
        return dateToConvert.toInstant()
          .atZone(ZoneId.systemDefault())
          .toLocalDate();
    }
}

package dk.digitalidentity.rc.controller.rest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import dk.digitalidentity.rc.controller.mvc.viewmodel.AttestationConfirmPersonalListDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AttestationConfirmRestDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AttestationConfirmShowDTO;
import dk.digitalidentity.rc.dao.model.AttachmentFile;
import dk.digitalidentity.rc.dao.model.AttestationNotification;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitAttestationPdf;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.CheckupIntervalEnum;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.security.RequireAssignerOrManagerRole;
import dk.digitalidentity.rc.security.RequireManagerRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.AttestationNotificationService;
import dk.digitalidentity.rc.service.AttestationService;
import dk.digitalidentity.rc.service.EmailQueueService;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.OrgUnitAttestationPdfService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.PositionService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.log4j.Log4j;

@RestController
@RequireAssignerOrManagerRole
@Log4j
public class AttestationRestController {

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private RoleGroupService roleGroupService;

	@Autowired
	private TemplateEngine templateEngine;

	@Autowired
	private OrgUnitAttestationPdfService orgUnitAttestationPdfService;

	@Autowired
	private EmailQueueService emailQueueService;

	@Autowired
	private EmailTemplateService emailTemplateService;

	@Autowired
	private SettingsService settingsService;
	
	@Autowired
	private AttestationNotificationService attestationNotificationService;
	
	@Autowired
	private AttestationService attestationService;
	
	@Autowired
	private PositionService positionService;
	
	@RequireManagerRole
	@PostMapping("/rest/attestations/confirm/{uuid}")
	public ResponseEntity<String> approveRequest(@PathVariable("uuid") String uuid, @RequestBody AttestationConfirmRestDTO confirmDTO) throws Exception {
		OrgUnit orgUnit = orgUnitService.getByUuid(uuid);
		if (orgUnit == null) {
			log.warn("Unable to fetch OrgUnit with uuid: " + uuid);
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		
		if (!isManager(orgUnit)) {
			log.warn("User tried to confirm for OU that they are not manager for");
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}

		if (settingsService.isAttestationRoleDeletionEnabled()) {
			// remove any personally assigned roles which the manager will not approve
			removePersonalRoles(confirmDTO.getToBeRemoved());
	
			// request removal of inherited roles that the manager will not approve
			requestRemovalOfRoles(orgUnit, confirmDTO.getMessage(), confirmDTO.getDtoShowToEmail(), null);
		} else {
			// request removal of roles that the manager will not approve
			requestRemovalOfRoles(orgUnit, confirmDTO.getMessage(), confirmDTO.getDtoShowToEmail(), confirmDTO.getDtoShowToBeRemoved());
		}
		
		// set lastAttested and update nextAttestation
		orgUnit.setLastAttested(new Date());
		User AttestingUser = userService.getByUserId(SecurityUtil.getUserId());
		if (AttestingUser != null) {
			orgUnit.setLastAttestedBy(AttestingUser.getName() + " (" + SecurityUtil.getUserId() + ")");
		}
		
		// set next attestation timestamp
		Date lastAttestation = (orgUnit.getNextAttestation() != null) ? orgUnit.getNextAttestation() : new Date();
		LocalDate afterThisTts = Instant.ofEpochMilli(lastAttestation.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
		
		// for PDF report
		Date currentDeadline = orgUnit.getNextAttestation();
		CheckupIntervalEnum interval = settingsService.getScheduledAttestationInterval();

		Date nextAttestationDate = attestationService.getNextAttestationDate(afterThisTts, attestationService.isSensitive(orgUnit));
		orgUnit.setNextAttestation(nextAttestationDate);
		
		orgUnitService.save(orgUnit);
		
		// cleanup notifications
		List<AttestationNotification> aNs = attestationNotificationService.getByOrgUnit(orgUnit);
		attestationNotificationService.deleteAll(aNs);

		// generate an attestation report
		byte[] pdfConfirmation = generateAttestationReport(confirmDTO, orgUnit, currentDeadline, interval);

		// Send pdf to manager and substitute, if they have emails
		if (pdfConfirmation != null) {
			Map<String, String> emails = new HashMap<>();
			
			String archiveEmail = settingsService.getEmailAttestationReport();
			if (!StringUtils.isEmpty(archiveEmail)) {
				emails.put(archiveEmail, "Arkiv");
			}

			User manager = orgUnit.getManager();
			if (!StringUtils.isEmpty(manager.getEmail())) {
				emails.put(manager.getEmail(), manager.getName());
			}
			
			if (manager.getManagerSubstitute() != null && !StringUtils.isEmpty(manager.getManagerSubstitute().getEmail())) {
				emails.put(manager.getManagerSubstitute().getEmail(), manager.getManagerSubstitute().getName());
			}

			StringBuilder fileNameBuilder = new StringBuilder();
			fileNameBuilder.append(LocalDate.now().toString());
			fileNameBuilder.append(" - ");
			fileNameBuilder.append(orgUnit.getName());
			fileNameBuilder.append(" - ");
			fileNameBuilder.append("attesteringsrapport.pdf");
			String fileName = fileNameBuilder.toString();
			
			EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_DOCUMENTATION);
			if (template.isEnabled()) {
				for (String email : emails.keySet()) {
					AttachmentFile attachmentFile = new AttachmentFile();
					attachmentFile.setContent(pdfConfirmation);
					attachmentFile.setFilename(fileName);

					List<AttachmentFile> attachments = new ArrayList<>();
					attachments.add(attachmentFile);

					String message = template.getMessage();
					message = message.replace(EmailTemplateService.RECEIVER_PLACEHOLDER, emails.get(email));
					message = message.replace(EmailTemplateService.ORGUNIT_PLACEHOLDER, orgUnit.getName());
					emailQueueService.queueEmail(email, template.getTitle(), message, template, attachments);
				}
			} else {
				log.info("Email template with type " + template.getTemplateType() + " is disabled. Emails were not sent.");
			}
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@GetMapping("/rest/users/attestations/{uuid}/download")
	@RequireManagerRole
	public ResponseEntity<?> downloadManager(@PathVariable("uuid") String uuid) throws IOException {
		OrgUnit orgUnit = orgUnitService.getByUuid(uuid);
		if (orgUnit == null) {
			log.warn("Unable to fetch OrgUnit with uuid: " + uuid);

			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		
		if (!isManager(orgUnit)) {
			log.warn("User tried to confirm for OU that they are not manager for");
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}
		
		OrgUnitAttestationPdf ouap = orgUnit.getAttestationPdf();
		if (ouap == null) {
			log.warn("Unable to fetch OrgUnitAttestationPdf from OrgUnit with uuid: " + uuid);

			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		StringBuilder fileNameBuilder = new StringBuilder();
		fileNameBuilder.append(sdf.format(orgUnit.getLastAttested()));
		fileNameBuilder.append(" - ");
		fileNameBuilder.append(orgUnit.getName());
		fileNameBuilder.append(" - ");
		fileNameBuilder.append("attesteringsrapport.pdf");
		String fileName = fileNameBuilder.toString();

		byte[] bytes = ouap.getPdf();
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		httpHeaders.setContentLength(bytes.length);
		httpHeaders.setContentDispositionFormData("attachment", fileName);

		return new ResponseEntity<>(bytes, httpHeaders, HttpStatus.OK);
	}

	@GetMapping("/rest/admin/attestations/{uuid}/download")
	@RequireAdministratorRole
	public ResponseEntity<?> downloadAdmin(@PathVariable("uuid") String uuid) throws IOException {
		OrgUnit orgUnit = orgUnitService.getByUuid(uuid);
		if (orgUnit == null) {
			log.warn("Unable to fetch OrgUnit with uuid: " + uuid);

			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		OrgUnitAttestationPdf ouap = orgUnit.getAttestationPdf();
		if (ouap == null) {
			log.warn("Unable to fetch OrgUnitAttestationPdf from OrgUnit with uuid: " + uuid);

			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		StringBuilder fileNameBuilder = new StringBuilder();
		fileNameBuilder.append(sdf.format(orgUnit.getLastAttested()));
		fileNameBuilder.append(" - ");
		fileNameBuilder.append(orgUnit.getName());
		fileNameBuilder.append(" - ");
		fileNameBuilder.append("attesteringsrapport.pdf");
		String fileName = fileNameBuilder.toString();

		byte[] bytes = ouap.getPdf();
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		httpHeaders.setContentLength(bytes.length);
		httpHeaders.setContentDispositionFormData("attachment", fileName);

		return new ResponseEntity<>(bytes, httpHeaders, HttpStatus.OK);
	}
	
	@PostMapping("/rest/admin/attestations/{uuid}/fastforward")
	@RequireAdministratorRole
	public ResponseEntity<?> fastForwardAttestation(@PathVariable("uuid") String uuid) throws IOException {
		OrgUnit orgUnit = orgUnitService.getByUuid(uuid);
		if (orgUnit == null) {
			log.warn("Unable to fetch OrgUnit with uuid: " + uuid);

			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		
		orgUnit.setNextAttestation(new Date());
		orgUnitService.save(orgUnit);

		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	private void removePersonalRoles(List<AttestationConfirmPersonalListDTO> list) {
		if (list.size() == 0) {
			return;
		}
		
		SecurityUtil.loginSystemAccount();
		try {
			for (AttestationConfirmPersonalListDTO tbr : list) {
				User user = userService.getByUuid(tbr.getUserUuid());
				if (user == null) {
					log.warn("Unknown user: " + tbr.getUserUuid());
					continue;
				}
	
				String roleType = tbr.getRoleType();
				if (roleType.equals("Jobfunktionsrolle")) {
					UserRole ur = userRoleService.getById(tbr.getRoleId());
					if (ur == null) {
						log.warn("Unknown UserRole: " + tbr.getRoleId());
						continue;
					}
					
					if (tbr.isFromPosition()) {
						positionService.removeUserRoleAssignment(user, tbr.getAssignmentId());
					}
					else {
						userService.removeUserRoleAssignment(user, tbr.getAssignmentId());
					}
					
				}
				else if (roleType.equals("Rollebuket")) {
					RoleGroup rg = roleGroupService.getById(tbr.getRoleId());
					if (rg == null) {
						log.warn("Unknown RoleGroup: " + tbr.getRoleId());
						continue;
					}
	
					if (tbr.isFromPosition()) {
						positionService.removeRoleGroupAssignment(user, tbr.getAssignmentId());
					}
					else {
						userService.removeRoleGroupAssignment(user, tbr.getAssignmentId());
					}
					
				}
				else {
					log.warn("Unknown roleType: " + roleType);
					continue;
				}
			}
		}
		finally {
			SecurityUtil.logoutSystemAccount();
		}
	}
	
	private void requestRemovalOfRoles(OrgUnit orgUnit, String managerMessage, List<AttestationConfirmShowDTO> unitRoles, List<AttestationConfirmShowDTO> personalRoles) throws Exception {
		String email = settingsService.getRemovalOfUnitRolesEmail();

		if (StringUtils.isEmpty(email) || (unitRoles.isEmpty() && personalRoles.isEmpty())) {
			log.warn("No email configured for sending removal requests");
		}
		else {
			Context ctxDelete = new Context();
			ctxDelete.setVariable("unitRoles", unitRoles);
			ctxDelete.setVariable("personalRoles", personalRoles);
			ctxDelete.setVariable("orgUnitName", orgUnit.getName());
			ctxDelete.setVariable("attestedBy", orgUnit.getLastAttestedBy());

			SimpleDateFormat simpleDateFormatDelete = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			ctxDelete.setVariable("time", simpleDateFormatDelete.format(new Date()));

			String htmlContentDelete = templateEngine.process("manager/attestations_unit_roles_pdf", ctxDelete);

			byte[] pdfDelete = null;

			// Create PDF document
			try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
				ITextRenderer renderer = new ITextRenderer();
				renderer.setDocumentFromString(htmlContentDelete);
				renderer.layout();
				renderer.createPDF(outputStream);

				pdfDelete = outputStream.toByteArray();
			}
			finally {
				;
			}

			if (pdfDelete != null) {
				AttachmentFile attachmentFile = new AttachmentFile();
				attachmentFile.setContent(pdfDelete);
				attachmentFile.setFilename("Anmodning.pdf");

				List<AttachmentFile> attachments = new ArrayList<>();
				attachments.add(attachmentFile);

				EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.REMOVE_UNIT_ROLES);
				if (template.isEnabled()) {
					String message = template.getMessage() + ((!StringUtils.isEmpty(managerMessage)) ? ("\n<br/><br/>\n<strong>Besked fra lederen:</strong>\n<br/>" + managerMessage) : "");
					message = message.replace(EmailTemplateService.RECEIVER_PLACEHOLDER, "it-afdeling");
					message = message.replace(EmailTemplateService.ORGUNIT_PLACEHOLDER, orgUnit.getName());
					emailQueueService.queueEmail(email, template.getTitle(), message, template, attachments);
				} else {
					log.info("Email template with type " + template.getTemplateType() + " is disabled. Email was not sent.");
				}
			}
		}
	}

	private byte[] generateAttestationReport(AttestationConfirmRestDTO confirmDTO, OrgUnit orgUnit, Date deadline, CheckupIntervalEnum interval) throws Exception {
		Context ctxConfirmation = new Context();
		ctxConfirmation.setVariable("aprovedPersonal", confirmDTO.getDtoShowAprovedPersonal());
		ctxConfirmation.setVariable("aprovedUnit", confirmDTO.getDtoShowAprovedUnit());
		ctxConfirmation.setVariable("toEmail", confirmDTO.getDtoShowToEmail());
		ctxConfirmation.setVariable("toBeRemoved", confirmDTO.getDtoShowToBeRemoved());
		ctxConfirmation.setVariable("orgUnitName", orgUnit.getName());
		ctxConfirmation.setVariable("attestedBy", orgUnit.getLastAttestedBy());
		ctxConfirmation.setVariable("message", confirmDTO.getMessage());

		SimpleDateFormat simpleDateFormatConfirmation = new SimpleDateFormat("yyyy-MM-dd");
		ctxConfirmation.setVariable("time", simpleDateFormatConfirmation.format(new Date()));
		ctxConfirmation.setVariable("deadline", simpleDateFormatConfirmation.format(deadline));
		ctxConfirmation.setVariable("interval", interval.getMessage());

		String htmlContentConfirmation = templateEngine.process("manager/attestations_pdf", ctxConfirmation);

		byte[] pdfConfirmation = null;
		
		// Create PDF document and save in db as byte[]
		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			ITextRenderer renderer = new ITextRenderer();
			renderer.setDocumentFromString(htmlContentConfirmation);
			renderer.layout();
			renderer.createPDF(outputStream);

			pdfConfirmation = outputStream.toByteArray();
			
			OrgUnitAttestationPdf ouap = new OrgUnitAttestationPdf();
			ouap.setPdf(pdfConfirmation);

			orgUnitAttestationPdfService.save(orgUnit, ouap);

			// We need to also save ou otherwise the relation between the now saved pdf and the ou is not neccesarily saved
			orgUnitService.save(orgUnit);
		}
		finally {
			;
		}
		
		return pdfConfirmation;
	}
	
	private boolean isManager(OrgUnit orgUnit) {
		String userId = SecurityUtil.getUserId();
		if (userId == null) {
			return false;
		}
		
		User user = userService.getByUserId(userId);
		if (user == null) {
			return false;
		}
		
		if (orgUnit.getManager() != null && orgUnit.getManager().getUuid().equals(user.getUuid())) {
			return true;
		}
		
		if (orgUnit.getManager() != null && orgUnit.getManager().getManagerSubstitute() != null && orgUnit.getManager().getManagerSubstitute().getUuid().equals(user.getUuid())) {
			return true;
		}

		return false;
	}
}

package dk.digitalidentity.rc.task;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.apache.commons.lang.LocaleUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.viewmodel.ReportForm;
import dk.digitalidentity.rc.controller.mvc.xlsview.ManagerReportXlsUtil;
import dk.digitalidentity.rc.dao.model.Attestation;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.CheckupIntervalEnum;
import dk.digitalidentity.rc.service.AttestationService;
import dk.digitalidentity.rc.service.EmailService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.PositionService;
import dk.digitalidentity.rc.service.ReportService;
import dk.digitalidentity.rc.service.SettingsService;
import lombok.extern.log4j.Log4j;

@Log4j
@Component
@EnableScheduling
@Transactional
public class AttestationTask {
	private static final String localeString = "da_DK";
	private Locale locale;
	
	@Value("${saml.baseUrl}")
	private String baseUrl;

	@Autowired
	private MessageSource messageSource;

	@Autowired
	private EmailService emailService;

	@Autowired
	private OrgUnitService orgUnitService;
	
	@Autowired
	private SettingsService settingsService;

	@Autowired
	private AttestationService attestationService;

	@Autowired
	private PositionService positionService;
	
	@Autowired
	private ManagerReportXlsUtil managerReportXlsUtil;
	
	@Autowired
	private ReportService reportService;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@PostConstruct
	public void setLocale() {
		this.locale = LocaleUtils.toLocale(localeString.replace('-', '_'));
	}
	
	@Scheduled(cron = "0 0 10 * * ?")
	public void reminder() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		if (!settingsService.isScheduledAttestationEnabled()) {
			log.debug("Attestation is disabled.");
			return;
		}
		
		List<OrgUnit> orgUnits = orgUnitService.getByNextAttestationToday();
		for (OrgUnit orgUnit : orgUnits) {
			if (orgUnit.getManager() == null) {
				log.warn("Cannot send reminder to manager for OrgUnit with uuid " + orgUnit.getUuid() + " as no manager exists");
			}
			else if (orgUnit.getManager().getEmail() == null) {
				log.warn("Cannot send reminder to manager for OrgUnit with uuid " + orgUnit.getUuid() + " as manager has no email");
			}
			else {
				ReportForm reportForm = new ReportForm();
				reportForm.setDate(LocalDate.now().toString());
				reportForm.setManager(orgUnit.getManager().getUuid());
				reportForm.setShowInactiveUsers(false);
				reportForm.setShowItSystems(true);
				reportForm.setShowOUs(true);
				reportForm.setShowKLE(true);
				reportForm.setShowUserRoles(true);
				reportForm.setShowUsers(true);
				reportForm.setName("Attesteringsrapport");

				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
				String deadline = sdf.format(orgUnit.getNextAttestation());

				sendEmail(orgUnit.getManager(), deadline, "html.email.attestation.message.overdue", reportForm);
				
				if (orgUnit.getManager().getManagerSubstitute() != null) {
					sendEmail(orgUnit.getManager().getManagerSubstitute(), deadline, "html.email.attestation.message", reportForm);
				}
			}
		}
	}

	@Scheduled(cron = "0 0 9 * * ?")
	public void attest() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		if (!settingsService.isScheduledAttestationEnabled()) {
			log.debug("Attestation is disabled.");
			return;
		}

		Date lastRun = settingsService.getScheduledAttestationLastRun();
		CheckupIntervalEnum interval = settingsService.getScheduledAttestationInterval();
		long dayInMonth = settingsService.getScheduledAttestationDayInMonth();
		if (!isRunDay(lastRun, interval, dayInMonth)) {
			log.debug("Attestation deadline not reached, skipping");
			return;
		}

		log.info("Running attestion job");
		
		Set<String> filter = settingsService.getScheduledAttestationFilter();
		if (filter.size() == 0) {
			log.info("No OrgUnits selected for attestering - skipping!");
			return;
		}

		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, 14);
		Date deadlineAsDate = cal.getTime();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		String deadline = sdf.format(deadlineAsDate);

		List<User> managers = orgUnitService.getManagers();
		for (User manager : managers) {
			if (StringUtils.isEmpty(manager.getEmail())) {
				log.warn("Could not send attestion mail to manager with no email address: " + manager.getName() + " / " + manager.getUuid());
				continue;
			}

			List<OrgUnit> orgUnits = orgUnitService.getByManagerMatchingUser(manager);
			
			orgUnits = orgUnits.stream().filter(o -> filter.contains(o.getUuid())).collect(Collectors.toList());
			if (orgUnits.size() == 0) {
				continue;
			}
			
			long count = 0;
			for (OrgUnit orgUnit : orgUnits) {
				List<User> tmp = flagUsersForAttestation(orgUnit);
				
				if (tmp.size() > 0) {
					orgUnit.setNextAttestation(deadlineAsDate);
					orgUnitService.save(orgUnit);
				}
				
				count += tmp.size();
			}

			if (count > 0) {
				ReportForm reportForm = new ReportForm();
				reportForm.setDate(LocalDate.now().toString());
				reportForm.setManager(manager.getUuid());
				reportForm.setShowInactiveUsers(false);
				reportForm.setShowItSystems(true);
				reportForm.setShowOUs(true);
				reportForm.setShowKLE(true);
				reportForm.setShowUserRoles(true);
				reportForm.setShowUsers(true);
				reportForm.setName("Attesteringsrapport");

				sendEmail(manager, deadline, "html.email.attestation.message", reportForm);
				
				if (manager.getManagerSubstitute() != null) {
					sendEmail(manager.getManagerSubstitute(), deadline, "html.email.attestation.message", reportForm);
				}
			}
		}

		log.info("Attestion job completed");
		
		settingsService.setScheduledAttestationLastRun(new Date());
	}

	private List<User> flagUsersForAttestation(OrgUnit orgUnit) {
		List<User> users = new ArrayList<>();
		
		// find all users
		List<Position> positions = positionService.findByOrgUnit(orgUnit);
		for (Position position : positions) {
			User user = position.getUser();

			// skip inactive users
			if (!user.isActive()) {
				continue;
			}
			
			// avoid duplicates
			if (users.stream().anyMatch(u -> user.getUuid().equals(u.getUuid()))) {
				continue;
			}
			
			// user must have some assigned role, otherwise no reason to attest
			if (position.getUserRoleAssignments().size() > 0 || position.getRoleGroupAssignments().size() > 0 || user.getUserRoleAssignments().size() > 0 || user.getRoleGroupAssignments().size() > 0) {
				users.add(user);
				
				Attestation attestation = new Attestation();
				attestation.setOrgUnit(orgUnit);
				attestation.setUser(user);
				attestation.setNotified(true);
				attestationService.save(attestation);
			}
		}
		
		return users;
	}

	private void sendEmail(User manager, String deadline, String messageRef, ReportForm reportForm) {
		ByteArrayOutputStream os = null;
		Workbook workbook = null;

		try {
			Map<String, Object> model = reportService.getReportModel(reportForm, locale);
			workbook = new HSSFWorkbook();
			managerReportXlsUtil.buildWorkbook(model, workbook);

			os = new ByteArrayOutputStream();
			workbook.write(os);

			String message = messageSource.getMessage(messageRef, new Object[] { deadline, baseUrl + "/ui/users/attestations" }, locale);

			emailService.sendMessageWithFileAttached(
					manager.getEmail(),
					messageSource.getMessage("html.email.attestation.title", null, locale),
					message,
					os.toByteArray(),
					"Lederrapport.xls");
			
			log.info("Send attestation email to: " + manager.getEmail());
		}
		catch (Exception ex) {
			log.error("Failed to create excel report", ex);
		}
		finally {
			try {
				if (os != null) {
					os.close();
				}

				if (workbook != null) {
					workbook.close();
				}
			}
			catch (IOException ex) {
				log.warn("Error occured while trying to close stream", ex);
			}
		}
	}

	private static boolean isRunDay(Date lastAttested, CheckupIntervalEnum interval, long dayInMonth) {
		LocalDate today = LocalDate.now();
		LocalDate lastRun = lastAttested.toInstant().atZone(ZoneId.of("Europe/Copenhagen")).toLocalDate();
		
		// catch multiple executions on the same day (should not happen, but still)
		if (lastRun.equals(today)) {
			return false;
		}
		
		// check for right day
		if (today.getDayOfMonth() != dayInMonth) {
			return false;
		}

		// check for right month
		switch (interval) {
			case MONTHLY:
				break;
			case QUARTERLY:
				if (today.getMonthValue() != 3 && today.getMonthValue() != 6 && today.getMonthValue() != 9 && today.getMonthValue() != 12) {
					return false;
				}
				break;
			case EVERY_HALF_YEAR:
				if (today.getMonthValue() != 3 && today.getMonthValue() != 9) {
					return false;
				}
				break;
			case YEARLY:
				if (today.getMonthValue() != 3) {
					return false;
				}
				break;
		}

		return true;
	}
}

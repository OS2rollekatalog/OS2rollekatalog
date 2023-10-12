package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.SettingsDao;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Setting;
import dk.digitalidentity.rc.dao.model.enums.CheckupIntervalEnum;
import dk.digitalidentity.rc.dao.model.enums.NotificationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SettingsService {
	private static final String SETTING_REQUEST_APPROVE_ENABLED = "RequestApproveEnabled";
	private static final String SETTING_REQUEST_APPROVE_SERVICEDESK_EMAIL = "RequestApproveServicedeskEmail";
	private static final String SETTING_SCHEDULED_ATTESTATION_ENABLED = "ScheduledAttestationEnabled";
	private static final String SETTING_SCHEDULED_ATTESTATION_INTERVAL = "ScheduledAttestationInterval";
	private static final String SETTING_SCHEDULED_ATTESTATION_FILTER_OLD = "ScheduledAttestationFilter";
	private static final String SETTING_SCHEDULED_ATTESTATION_EXCEPTED_ORG_UNITS = "ScheduledAttestationExceptedOrgUnits";
	private static final String SETTING_SCHEDULED_ATTESTATION_LAST_RUN = "ScheduledAttestationLastRun";
	private static final String SETTING_IT_SYSTEM_CHANGE_EMAIL = "ItSystemChangeEmail";
	private static final String SETTING_ATTESTATIONCHANGE_EMAIL = "RemovalOfUnitRolesEmail";
	private static final String SETTING_AD_ATTESTATION = "AttestationADEnabled";
	private static final String SETTING_RUN_CICS = "RunCics";
	private static final String SETTING_IT_SYSTEM_DEFAULT_HIDDEN_ENABLED = "ItSystemHiddenByDefault";
	private static final String SETTING_FIRST_ATTESTATION_DATE = "FirstAttestationDate";
	private static final String SETTING_MITID_ERHVERV_MIGRATION_PERFORMED = "MitIDErhvervMigrationPerformed";

	private static final String SETTING_SYNC_ASSIGNMENTS_TO_OU_PERFORMED = "SyncOrgUnitOnRoleAssignmentsPerformed";

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private SettingsDao settingsDao;

	public boolean isRequestApproveEnabled() {
		return isKeyEnabled(SETTING_REQUEST_APPROVE_ENABLED);
	}
	
	public void setRequestApproveEnabled(boolean enabled) {
		setKeyEnabled(enabled, SETTING_REQUEST_APPROVE_ENABLED);
	}

	public String getRequestApproveServicedeskEmail() {
		Setting setting = settingsDao.findByKey(SETTING_REQUEST_APPROVE_SERVICEDESK_EMAIL);
		if (setting == null) {
			return "";
		}

		return setting.getValue();
	}
	
	public void setRequestApproveServicedeskEmail(String email) {
		Setting setting = settingsDao.findByKey(SETTING_REQUEST_APPROVE_SERVICEDESK_EMAIL);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_REQUEST_APPROVE_SERVICEDESK_EMAIL);
		}
		
		setting.setValue(email);
		settingsDao.save(setting);
	}
	
	public String getAttestationChangeEmail() {
		Setting setting = settingsDao.findByKey(SETTING_ATTESTATIONCHANGE_EMAIL);
		if (setting == null) {
			return "";
		}

		return setting.getValue();
	}
	
	public void setAttestationChangeEmail(String email) {
		Setting setting = settingsDao.findByKey(SETTING_ATTESTATIONCHANGE_EMAIL);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_ATTESTATIONCHANGE_EMAIL);
		}
		
		setting.setValue(email);
		settingsDao.save(setting);
	}

	public Set<String> getScheduledAttestationFilter() {
		Setting setting = settingsDao.findByKey(SETTING_SCHEDULED_ATTESTATION_EXCEPTED_ORG_UNITS);
		Setting oldSetting = settingsDao.findByKey(SETTING_SCHEDULED_ATTESTATION_FILTER_OLD);

		// migration from old attestation module
		if (oldSetting != null && StringUtils.hasLength(oldSetting.getValue())) {
			List<String> includedOrgUnits = Arrays.asList(oldSetting.getValue().split(","));
			List<OrgUnit> allOrgUnits = orgUnitService.getAll();
			String excludedOrgUnits = allOrgUnits.stream().map(OrgUnit::getUuid).filter(uuid -> !includedOrgUnits.contains(uuid)).collect(Collectors.joining(","));

			if (setting == null) {
				setting = new Setting();
				setting.setKey(SETTING_SCHEDULED_ATTESTATION_EXCEPTED_ORG_UNITS);
			}
			setting.setValue(excludedOrgUnits);

			// save
			setting = settingsDao.save(setting);
			settingsDao.delete(oldSetting);
		}

		if (setting == null || !StringUtils.hasLength(setting.getValue())) {
			return new HashSet<>();
		}
		
		String[] uuids = setting.getValue().split(",");
		
		return new HashSet<>(Arrays.asList(uuids));
	}

	public void setScheduledAttestationFilter(Set<String> filter) {
		Setting setting = settingsDao.findByKey(SETTING_SCHEDULED_ATTESTATION_EXCEPTED_ORG_UNITS);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_SCHEDULED_ATTESTATION_EXCEPTED_ORG_UNITS);
		}
		
		setting.setValue(String.join(",", filter));
		settingsDao.save(setting);
	}

	public LocalDate getFirstAttestationDate() {
		Setting setting = settingsDao.findByKey(SETTING_FIRST_ATTESTATION_DATE);
		if (setting == null) {
			return LocalDate.now().plusMonths(1);
		}

		return LocalDate.parse(setting.getValue());
	}

	public void setFirstAttestationDate(LocalDate date) {
		Setting setting = settingsDao.findByKey(SETTING_FIRST_ATTESTATION_DATE);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_FIRST_ATTESTATION_DATE);
		}

		setting.setValue(date.toString());
		settingsDao.save(setting);
	}

	public boolean firstAttestationDateIsNull() {
		Setting setting = settingsDao.findByKey(SETTING_FIRST_ATTESTATION_DATE);
		if (setting == null) {
			return true;
		}

		return false;
	}


	/// helper methods

	private boolean isKeyEnabled(String key) {
		Setting setting = settingsDao.findByKey(key);
		if (setting != null) {
			if ("true".equals(setting.getValue())) {
				return true;
			}
		}

		return false;
	}
	
	private void setKeyEnabled(boolean enabled, String key) {
		Setting setting = settingsDao.findByKey(key);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(key);
		}
		
		setting.setValue(enabled ? "true" : "false");
		settingsDao.save(setting);
	}

	public boolean isScheduledAttestationEnabled() {
		return isKeyEnabled(SETTING_SCHEDULED_ATTESTATION_ENABLED);
	}

	public void setScheduledAttestationEnabled(boolean enabled) {
		setKeyEnabled(enabled, SETTING_SCHEDULED_ATTESTATION_ENABLED);
	}

	public CheckupIntervalEnum getScheduledAttestationInterval() {
		Setting setting = settingsDao.findByKey(SETTING_SCHEDULED_ATTESTATION_INTERVAL);
		if (setting == null) {
			return CheckupIntervalEnum.EVERY_HALF_YEAR;
		}

		// migration from old attestation module
		if (setting.getValue().equals("MONTHLY") || setting.getValue().equals("QUARTERLY")) {
			setting.setValue(CheckupIntervalEnum.EVERY_HALF_YEAR.toString());
			settingsDao.save(setting);
			return CheckupIntervalEnum.EVERY_HALF_YEAR;
		}

		return CheckupIntervalEnum.valueOf(setting.getValue());
	}
	
	public void setScheduledAttestationInterval(CheckupIntervalEnum interval) {
		Setting setting = settingsDao.findByKey(SETTING_SCHEDULED_ATTESTATION_INTERVAL);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_SCHEDULED_ATTESTATION_INTERVAL);
		}
		
		setting.setValue(interval.toString());
		settingsDao.save(setting);
	}
	
	public Date getScheduledAttestationLastRun() {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");

		try {
			Setting setting = settingsDao.findByKey(SETTING_SCHEDULED_ATTESTATION_LAST_RUN);
			if (setting == null) {
				return format.parse("1979-05-21");
			}
	
			return format.parse(setting.getValue());
		}
		catch (Exception ex) {
			throw new RuntimeException("Failed to parse", ex);
		}
	}
	
	public void setScheduledAttestationLastRun(Date date) {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		String dateString = format.format(date);

		Setting setting = settingsDao.findByKey(SETTING_SCHEDULED_ATTESTATION_LAST_RUN);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_SCHEDULED_ATTESTATION_LAST_RUN);
		}
		
		setting.setValue(dateString);
		settingsDao.save(setting);
	}

	public String getItSystemChangeEmail() {
		Setting setting = settingsDao.findByKey(SETTING_IT_SYSTEM_CHANGE_EMAIL);
		if (setting == null) {
			return "";
		}

		return setting.getValue();
	}

	public void setItSystemChangeEmail(String email) {
		Setting setting = settingsDao.findByKey(SETTING_IT_SYSTEM_CHANGE_EMAIL);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_IT_SYSTEM_CHANGE_EMAIL);
		}
		
		setting.setValue(email);
		settingsDao.save(setting);
	}

	public boolean isADAttestationEnabled() {
		return isKeyEnabled(SETTING_AD_ATTESTATION);
	}

	public void setADAttestationEnabled(boolean enabled) {
		setKeyEnabled(enabled, SETTING_AD_ATTESTATION);
	}
	
	public boolean isNotificationTypeEnabled(NotificationType notificationType) {
		return getBooleanWithDefault(notificationType.toString(), true);
	}
	
	private boolean getBooleanWithDefault(String key, boolean defaultValue) {
		Setting setting = settingsDao.findByKey(key);
		if (setting != null) {
			return Boolean.parseBoolean(setting.getValue());
		}

		return defaultValue;
	}
	
	public void setNotificationTypeEnabled(NotificationType notificationType, boolean enabled) {
		Setting setting = settingsDao.findByKey(notificationType.toString());
		if (setting == null) {
			setting = new Setting();
			setting.setKey(notificationType.toString());
		}

		setting.setValue(Boolean.toString(enabled));
		settingsDao.save(setting);
	}
	
	public boolean isRunCics() {
		return isKeyEnabled(SETTING_RUN_CICS);
	}
	
	public void setRunCics(boolean enabled) {
		setKeyEnabled(enabled, SETTING_RUN_CICS);
	}

	public boolean isItSystemsHiddenByDefault() {
		return isKeyEnabled(SETTING_IT_SYSTEM_DEFAULT_HIDDEN_ENABLED);
	}

	public void setItSystemsHiddenByDefault(boolean enabled) {
		setKeyEnabled(enabled, SETTING_IT_SYSTEM_DEFAULT_HIDDEN_ENABLED);
	}

	public Setting getByKey(String key) {
		return settingsDao.findByKey(key);
	}

	public void save(Setting setting) {
		settingsDao.save(setting);
	}

	public boolean isMitIDErhvervMigrationPerformed() {
		return isKeyEnabled(SETTING_MITID_ERHVERV_MIGRATION_PERFORMED);
	}

	public void setMitIDErhvervMigrationPerformed() {
		setKeyEnabled(true, SETTING_MITID_ERHVERV_MIGRATION_PERFORMED);
	}

	public boolean isSyncOrgUnitOnRoleAssignmentsPerformed() {
		return isKeyEnabled(SETTING_SYNC_ASSIGNMENTS_TO_OU_PERFORMED);
	}

	public void setSyncOrgUnitOnRoleAssignmentsPerformed() {
		setKeyEnabled(true, SETTING_SYNC_ASSIGNMENTS_TO_OU_PERFORMED);
	}
}

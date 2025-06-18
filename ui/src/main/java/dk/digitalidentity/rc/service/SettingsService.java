package dk.digitalidentity.rc.service;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.rolerequest.model.enums.ApproverOption;
import dk.digitalidentity.rc.rolerequest.model.enums.ReasonOption;
import dk.digitalidentity.rc.rolerequest.model.enums.RequesterOption;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.dao.SettingsDao;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Setting;
import dk.digitalidentity.rc.dao.model.enums.CheckupIntervalEnum;
import dk.digitalidentity.rc.dao.model.enums.NotificationType;
import dk.digitalidentity.rc.dao.model.enums.Settings;
import dk.digitalidentity.rc.log.AuditLogContextHolder;
import dk.digitalidentity.rc.log.AuditLogger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SettingsService {

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private SettingsDao settingsDao;

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private MessageSource messageSource;

	public boolean isRequestApproveEnabled() {
		return isKeyEnabled(Settings.SETTING_REQUEST_APPROVE_ENABLED.getKey());
	}

	public void setRequestApproveEnabled(boolean enabled) {
		boolean changed = setKeyEnabled(enabled, Settings.SETTING_REQUEST_APPROVE_ENABLED.getKey());
		if (changed) {
			AuditLogContextHolder.getContext().addArgument("Ny værdi", (enabled ? "true" : "false"));
			auditLogger.logSetting(settingsDao.findByKey(Settings.SETTING_REQUEST_APPROVE_ENABLED.getKey()), null, null, getPrettyName(Settings.SETTING_REQUEST_APPROVE_ENABLED));
			AuditLogContextHolder.clearContext();
		}
	}

	public String getRequestApproveServicedeskEmail() {
		Setting setting = settingsDao.findByKey(Settings.SETTING_REQUEST_APPROVE_SERVICEDESK_EMAIL.getKey());
		if (setting == null) {
			return "";
		}

		return setting.getValue();
	}

	public void setRequestApproveServicedeskEmail(String email) {
		createOrUpdateSetting(Settings.SETTING_REQUEST_APPROVE_SERVICEDESK_EMAIL, email);
	}

	public String getAttestationChangeEmail() {
		Setting setting = settingsDao.findByKey(Settings.SETTING_ATTESTATIONCHANGE_EMAIL.getKey());
		if (setting == null) {
			return "";
		}

		return setting.getValue();
	}

	public void setAttestationChangeEmail(String email) {
		createOrUpdateSetting(Settings.SETTING_ATTESTATIONCHANGE_EMAIL, email);
	}

	public Set<String> getScheduledAttestationFilter() {
		Setting setting = settingsDao.findByKey(Settings.SETTING_SCHEDULED_ATTESTATION_EXCEPTED_ORG_UNITS.getKey());
		Setting oldSetting = settingsDao.findByKey(Settings.SETTING_SCHEDULED_ATTESTATION_FILTER_OLD.getKey());

		// migration from old attestation module
		if (oldSetting != null && StringUtils.hasLength(oldSetting.getValue())) {
			List<String> includedOrgUnits = Arrays.asList(oldSetting.getValue().split(","));
			List<OrgUnit> allOrgUnits = orgUnitService.getAll();
			String excludedOrgUnits = allOrgUnits.stream().map(OrgUnit::getUuid).filter(uuid -> !includedOrgUnits.contains(uuid)).collect(Collectors.joining(","));

			if (setting == null) {
				setting = new Setting();
				setting.setKey(Settings.SETTING_SCHEDULED_ATTESTATION_EXCEPTED_ORG_UNITS.getKey());
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
		createOrUpdateSetting(Settings.SETTING_SCHEDULED_ATTESTATION_EXCEPTED_ORG_UNITS, String.join(",", filter));
	}

	public LocalDate getFirstAttestationDate() {
		Setting setting = settingsDao.findByKey(Settings.SETTING_FIRST_ATTESTATION_DATE.getKey());
		if (setting == null) {
			return LocalDate.now().plusMonths(1);
		}

		return LocalDate.parse(setting.getValue());
	}

	public void setFirstAttestationDate(LocalDate date) {
		createOrUpdateSetting(Settings.SETTING_FIRST_ATTESTATION_DATE, date.toString());
	}

	public boolean firstAttestationDateIsNull() {
		Setting setting = settingsDao.findByKey(Settings.SETTING_FIRST_ATTESTATION_DATE.getKey());
		return setting == null;
	}


	/// helper methods

	private void createOrUpdateSetting(Settings settingEnum, String value) {
		createOrUpdateSetting(settingEnum, value, true);
	}

	private void createOrUpdateSetting(Settings settingEnum, String value, final boolean auditLog) {
		Setting setting = settingsDao.findByKey(settingEnum.getKey());
		if (setting == null) {
			setting = new Setting();
			setting.setKey(settingEnum.getKey());
		}

		boolean changed = !Objects.equals(setting.getValue(), value);
		setting.setValue(value);
		settingsDao.save(setting);

		if (changed) {
			//Enrich orgUnit uuid so it looks better in auditlog page
			if (Objects.equals(settingEnum, Settings.SETTING_SCHEDULED_ATTESTATION_EXCEPTED_ORG_UNITS) && value != null) {
					value = Arrays.asList(value.split(",")).stream()
							.map(uuid -> orgUnitService.getByUuid(uuid)).filter(Objects::nonNull)
							.map(ou -> ou.getName()).collect(Collectors.joining(","));
			}
			if (auditLog) {
				AuditLogContextHolder.getContext().addArgument("Ny værdi", value);
				auditLogger.logSetting(setting, null, null, getPrettyName(settingEnum));
				AuditLogContextHolder.clearContext();
			}
		}
	}

	private boolean isKeyEnabled(String key) {
		Setting setting = settingsDao.findByKey(key);
		if (setting != null) {
			if ("true".equals(setting.getValue())) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Sets the value for given key
	 * @param enabled
	 * @param key
	 * @return returns true if setting was changed.
	 */
	private boolean setKeyEnabled(boolean enabled, String key) {
		Setting setting = settingsDao.findByKey(key);
		boolean changed = false;
		if (setting == null) {
			setting = new Setting();
			setting.setKey(key);
		}

		changed = !Objects.equals(setting.getValue(), (enabled ? "true" : "false"));
		setting.setValue(enabled ? "true" : "false");
		settingsDao.save(setting);
		return changed;
	}

	private String getPrettyName(Settings settingEnum) {
		String prettyName;
		try {
			prettyName = messageSource.getMessage(settingEnum.getMessage(), null, new Locale("da-DK"));
		} catch (Exception e) {
			log.warn("Entry missing in messages.properties for " + settingEnum, e);
			prettyName = null;
		}
		return prettyName;
	}

	public boolean isScheduledAttestationEnabled() {
		return isKeyEnabled(Settings.SETTING_SCHEDULED_ATTESTATION_ENABLED.getKey());
	}

	public void setScheduledAttestationEnabled(boolean enabled) {
		boolean changed = setKeyEnabled(enabled, Settings.SETTING_SCHEDULED_ATTESTATION_ENABLED.getKey());
		if (changed) {
			AuditLogContextHolder.getContext().addArgument("Ny værdi", (enabled ? "true" : "false"));
			auditLogger.logSetting(settingsDao.findByKey(Settings.SETTING_SCHEDULED_ATTESTATION_ENABLED.getKey()), null, null, getPrettyName(Settings.SETTING_SCHEDULED_ATTESTATION_ENABLED));
			AuditLogContextHolder.clearContext();
		}
	}

	public CheckupIntervalEnum getScheduledAttestationInterval() {
		Setting setting = settingsDao.findByKey(Settings.SETTING_SCHEDULED_ATTESTATION_INTERVAL.getKey());
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
		createOrUpdateSetting(Settings.SETTING_SCHEDULED_ATTESTATION_INTERVAL, interval.toString());
	}

	public Date getScheduledAttestationLastRun() {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");

		try {
			Setting setting = settingsDao.findByKey(Settings.SETTING_SCHEDULED_ATTESTATION_LAST_RUN.getKey());
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
		createOrUpdateSetting(Settings.SETTING_SCHEDULED_ATTESTATION_LAST_RUN, dateString, false);
	}

	public String getVikarRegEx() {
		Setting setting = settingsDao.findByKey(Settings.SETTING_VIKAR_REGEX.getKey());
		if (setting == null) {
			return "";
		}

		return setting.getValue();
	}

	public void setVikarRegEx(String regex) {
		createOrUpdateSetting(Settings.SETTING_VIKAR_REGEX, regex);
	}

	public String getItSystemChangeEmail() {
		Setting setting = settingsDao.findByKey(Settings.SETTING_IT_SYSTEM_CHANGE_EMAIL.getKey());
		if (setting == null) {
			return "";
		}

		return setting.getValue();
	}

	public void setItSystemChangeEmail(String email) {
		createOrUpdateSetting(Settings.SETTING_IT_SYSTEM_CHANGE_EMAIL, email);
	}

	@Cacheable(value = "excludedOUs")
	public Set<String> getExcludedOUs() {
		Setting setting = settingsDao.findByKey(Settings.SETTING_EXCLUDED_OUS.getKey());

		if (setting == null || !StringUtils.hasLength(setting.getValue())) {
			return new HashSet<>();
		}

		String[] uuids = setting.getValue().split(",");

		return new HashSet<>(Arrays.asList(uuids));
	}

	@CacheEvict(value = "excludedOUs", allEntries = true)
	public void setExcludedOUs(Set<String> filter) {
		createOrUpdateSetting(Settings.SETTING_EXCLUDED_OUS, String.join(",", filter));
	}

	public boolean isADAttestationEnabled() {
		return isKeyEnabled(Settings.SETTING_AD_ATTESTATION.getKey());
	}

	public void setADAttestationEnabled(boolean enabled) {
		boolean changed = setKeyEnabled(enabled, Settings.SETTING_AD_ATTESTATION.getKey());
		if (changed) {
			AuditLogContextHolder.getContext().addArgument("Ny værdi", (enabled ? "true" : "false"));
			auditLogger.logSetting(settingsDao.findByKey(Settings.SETTING_AD_ATTESTATION.getKey()), null, null, getPrettyName(Settings.SETTING_AD_ATTESTATION));
			AuditLogContextHolder.clearContext();
		}
	}

	public boolean isDontSendMailToManagerEnabled() {
		return isKeyEnabled(Settings.SETTING_DONT_SEND_MAIL_TO_MANAGER.getKey());
	}

	public void setDontSendMailToManagerEnabled(boolean enabled) {
		setKeyEnabled(enabled, Settings.SETTING_DONT_SEND_MAIL_TO_MANAGER.getKey());
	}

	public boolean isAttestationRequestChangesEnabled() {
		final Setting setting = settingsDao.findByKey(Settings.SETTING_ALLOW_CHANGE_REQUEST_ATTESTATION.getKey());
		if (setting == null) {
			return true;
		}
		return setting.getValue().equals("true");
	}

	public void setAttestationRequestChangesEnabled(final boolean enabled) {
		boolean changed = setKeyEnabled(enabled, Settings.SETTING_ALLOW_CHANGE_REQUEST_ATTESTATION.getKey());
		if (changed) {
			AuditLogContextHolder.getContext().addArgument("Ny værdi", (enabled ? "true" : "false"));
			auditLogger.logSetting(settingsDao.findByKey(Settings.SETTING_ALLOW_CHANGE_REQUEST_ATTESTATION.getKey()), null, null, getPrettyName(Settings.SETTING_ALLOW_CHANGE_REQUEST_ATTESTATION));
			AuditLogContextHolder.clearContext();
		}
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
		boolean changed = false;
		if (setting == null) {
			setting = new Setting();
			setting.setKey(notificationType.toString());
		}

		changed = !Objects.equals(setting.getValue(), (enabled ? "true" : "false"));
		setting.setValue(Boolean.toString(enabled));
		settingsDao.save(setting);

		if (changed) {
			AuditLogContextHolder.getContext().addArgument("Ny værdi", (enabled ? "true" : "false"));
			String prettyname = messageSource.getMessage(notificationType.getMessage(), null, new Locale("da-DK"));
			auditLogger.logSetting(setting, null, null, prettyname);
			AuditLogContextHolder.clearContext();
		}
	}

	public boolean isRunCics() {
		return isKeyEnabled(Settings.SETTING_RUN_CICS.getKey());
	}

	public void setRunCics(boolean enabled) {
		setKeyEnabled(enabled, Settings.SETTING_RUN_CICS.getKey());
	}

	public boolean isItSystemsHiddenByDefault() {
		return isKeyEnabled(Settings.SETTING_IT_SYSTEM_DEFAULT_HIDDEN_ENABLED.getKey());
	}

	public void setItSystemsHiddenByDefault(boolean enabled) {
		boolean changed = setKeyEnabled(enabled, Settings.SETTING_IT_SYSTEM_DEFAULT_HIDDEN_ENABLED.getKey());
		if (changed) {
			AuditLogContextHolder.getContext().addArgument("Ny værdi", (enabled ? "true" : "false"));
			auditLogger.logSetting(settingsDao.findByKey(Settings.SETTING_IT_SYSTEM_DEFAULT_HIDDEN_ENABLED.getKey()), null, null, getPrettyName(Settings.SETTING_IT_SYSTEM_DEFAULT_HIDDEN_ENABLED));
			AuditLogContextHolder.clearContext();
		}
	}

	public Setting getByKey(String key) {
		return settingsDao.findByKey(key);
	}

	public void save(Setting setting) {
		settingsDao.save(setting);
	}

	public boolean isMitIDErhvervMigrationPerformed() {
		return isKeyEnabled(Settings.SETTING_MITID_ERHVERV_MIGRATION_PERFORMED.getKey());
	}

	public void setMitIDErhvervMigrationPerformed() {
		setKeyEnabled(true, Settings.SETTING_MITID_ERHVERV_MIGRATION_PERFORMED.getKey());
	}

	public boolean isBlockAllEmailTransmissions() {
		return getBooleanWithDefault(Settings.SETTING_BLOCK_ALL_EMAIL_TRANSMISSIONS.getKey(), false);
	}

	public void setBlockAllEmailTransmissions(boolean enabled) {
		setKeyEnabled(enabled, Settings.SETTING_BLOCK_ALL_EMAIL_TRANSMISSIONS.getKey());
	}

	public int getEmailQueueLimit() {
		Setting setting = settingsDao.findByKey(Settings.SETTING_EMAIL_QUEUE_LIMIT.getKey());
		if (setting == null) {
			return 0;
		}

		return Integer.parseInt(setting.getValue());
	}

	public void setEmailQueueLimit(int size) {
		Setting setting = settingsDao.findByKey(Settings.SETTING_EMAIL_QUEUE_LIMIT.getKey());
		if (setting == null) {
			setting = new Setting();
			setting.setKey(Settings.SETTING_EMAIL_QUEUE_LIMIT.getKey());
		}

		setting.setValue(Integer.toString(size));
		settingsDao.save(setting);
	}

	public int getCurrentInstalledRank() {
		Setting setting = settingsDao.findByKey(Settings.SETTING_CURRENT_INSTALLED_RANK.getKey());
		if(setting == null) {
			return 0;
		}
		return Integer.parseInt(setting.getValue());
	}

	public void setCurrentInstalledRank(int rank) {
		Setting setting = settingsDao.findByKey(Settings.SETTING_CURRENT_INSTALLED_RANK.getKey());
		if(setting == null) {
			setting = new Setting();
			setting.setKey(Settings.SETTING_CURRENT_INSTALLED_RANK.getKey());
		}
		setting.setValue(Integer.toString(rank));
		settingsDao.save(setting);
	}

	public RequesterOption getRolerequestRequester() {
		Setting setting = settingsDao.findByKey(Settings.SETTING_ROLEREQUEST_REQUESTER.getKey());
		if (setting == null) {
			return RequesterOption.NONE;
		}

		return RequesterOption.valueOf(setting.getValue());
	}

	public void setRolerequestRequester(RequesterOption requesterSetting) {
		Setting setting = settingsDao.findByKey(Settings.SETTING_ROLEREQUEST_REQUESTER.getKey());
		if (setting == null) {
			setting = new Setting();
			setting.setKey(Settings.SETTING_ROLEREQUEST_REQUESTER.getKey());
		}
		setting.setValue(requesterSetting.name());
		settingsDao.save(setting);

	}

	public ApproverOption getRolerequestApprover() {
		Setting setting = settingsDao.findByKey(Settings.SETTING_ROLEREQUEST_APPROVER.getKey());
		if (setting == null) {
			return ApproverOption.MANAGERORAUTHRESPONSIBLE;
		}

		return ApproverOption.valueOf(setting.getValue());
	}

	public void setRolerequestApprover(ApproverOption approverSetting) {
		Setting setting = settingsDao.findByKey(Settings.SETTING_ROLEREQUEST_APPROVER.getKey());
		if (setting == null) {
			setting = new Setting();
			setting.setKey(Settings.SETTING_ROLEREQUEST_APPROVER.getKey());
		}
		setting.setValue(approverSetting.name());
		settingsDao.save(setting);
	}

	public ReasonOption getRolerequestReason() {
		Setting setting = settingsDao.findByKey(Settings.SETTING_ROLEREQUEST_REASON.getKey());
		if (setting == null) {
			return ReasonOption.OBLIGATORY;
		}

		return ReasonOption.valueOf(setting.getValue());
	}

	public void setRolerequestReason(ReasonOption approverSetting) {
		Setting setting = settingsDao.findByKey(Settings.SETTING_ROLEREQUEST_REASON.getKey());
		if (setting == null) {
			setting = new Setting();
			setting.setKey(Settings.SETTING_ROLEREQUEST_REASON.getKey());
		}
		setting.setValue(approverSetting.name());
		settingsDao.save(setting);
	}

	public boolean getOnlyRecommendRoles() {
		return isKeyEnabled(Settings.SETTING_ROLEREQUEST_ONLY_RECOMMENDED_ROLES.getKey());
	}

	public void setOnlyRecommendRoles(boolean enabled) {
		setKeyEnabled(enabled, Settings.SETTING_ROLEREQUEST_ONLY_RECOMMENDED_ROLES.getKey());
	}

	public void setZonedDateTime(final String kitosKey, final ZonedDateTime zonedDateTime) {
		Setting setting = settingsDao.findByKey(kitosKey);
		if(setting == null) {
			setting = new Setting();
			setting.setKey(kitosKey);
		}
		setting.setValue(zonedDateTime.toOffsetDateTime().toString());
		settingsDao.save(setting);
	}

	public ZonedDateTime getZonedDateTime(final String kitosKey, final ZonedDateTime defaultVal) {
		Setting setting = settingsDao.findByKey(kitosKey);
		return setting == null ? defaultVal : OffsetDateTime.parse(setting.getValue()).toZonedDateTime();
	}

	public boolean isCaseNumberEnabled() {
		return isKeyEnabled(Settings.SETTING_CASE_NUMBER_ENABLED.getKey());
	}

	public void setCaseNumberEnabled(boolean enabled) {
		boolean changed = setKeyEnabled(enabled, Settings.SETTING_CASE_NUMBER_ENABLED.getKey());
		if (changed) {
			AuditLogContextHolder.getContext().addArgument("Ny værdi", (enabled ? "true" : "false"));
			auditLogger.logSetting(settingsDao.findByKey(Settings.SETTING_CASE_NUMBER_ENABLED.getKey()), null, null, getPrettyName(Settings.SETTING_CASE_NUMBER_ENABLED));
			AuditLogContextHolder.clearContext();
		}
	}
}

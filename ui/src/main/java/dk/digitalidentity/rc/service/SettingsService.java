package dk.digitalidentity.rc.service;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.dao.model.enums.OrgUnitLevel;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.ReasonOption;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
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
@EnableCaching
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
			auditLogger.logSetting(settingsDao.findByKey(Settings.SETTING_REQUEST_APPROVE_ENABLED.getKey()),
				null, null, getPrettyName(Settings.SETTING_REQUEST_APPROVE_ENABLED));
			AuditLogContextHolder.clearContext();
		}
	}

	public boolean isShowSingleTableInRequestApproveEnabled() {
		return isKeyEnabled(Settings.SETTING_SHOW_SINGLE_TABLE_FOR_REQUEST_APPROVE.getKey());
	}

	public void setShowSingleTableInRequestApproveEnabled(boolean enabled) {
		boolean changed = setKeyEnabled(enabled, Settings.SETTING_SHOW_SINGLE_TABLE_FOR_REQUEST_APPROVE.getKey());
		if (changed) {
			AuditLogContextHolder.getContext().addArgument("Ny værdi", (enabled ? "true" : "false"));
			auditLogger.logSetting(settingsDao.findByKey(Settings.SETTING_REQUEST_APPROVE_ENABLED.getKey()), null, null, getPrettyName(Settings.SETTING_SHOW_SINGLE_TABLE_FOR_REQUEST_APPROVE));
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

	public Integer getRemoveDirectAssignmentsForDisabled() {
		Setting setting = settingsDao.findByKey(Settings.SETTING_REMOVE_DIRECT_ASSIGNMENTS_FOR_DISABLED.getKey());
		if (setting == null || !StringUtils.hasLength(setting.getValue())) {
			return 180;
		}

		try {
			return Integer.parseInt(setting.getValue());
		} catch (NumberFormatException e) {
			log.warn("Invalid value for RemoveDirectAssignmentsForDisabled setting: {}", setting.getValue());
			return 180;
		}
	}

	public void setRemoveDirectAssignmentsForDisabled(Integer days) {
		String value = (days != null) ? days.toString() : "";
		createOrUpdateSetting(Settings.SETTING_REMOVE_DIRECT_ASSIGNMENTS_FOR_DISABLED, value);
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

	public Set<String> getScheduledAttestationOptedInOrgUnits() {
		Setting setting = settingsDao.findByKey(Settings.SETTING_SCHEDULED_ATTESTATION_OPTED_IN_ORG_UNITS.getKey());

		if (setting == null || !StringUtils.hasLength(setting.getValue())) {
			return new HashSet<>();
		}

		String[] uuids = setting.getValue().split(",");

		return new HashSet<>(Arrays.asList(uuids));
	}

	public void setScheduledAttestationOptedInOrgUnits(Set<String> filter) {
		createOrUpdateSetting(Settings.SETTING_SCHEDULED_ATTESTATION_OPTED_IN_ORG_UNITS, String.join(",", filter));
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

	private boolean getKeyOrDefault(String key, boolean defaultValue) {
		Setting setting = settingsDao.findByKey(key);
		if (setting != null) {
			return "true".equals(setting.getValue());
		}

		return defaultValue;
	}

	/**
	 * Sets the value for given key
	 *
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
			prettyName = messageSource.getMessage(settingEnum.getMessage(), null, Locale.of("da", "DK"));
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
		} catch (Exception ex) {
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

	@Cacheable(value = "SettingsCache-getExcludedOUs")
	public Set<String> getExcludedOUs() {
		Setting setting = settingsDao.findByKey(Settings.SETTING_EXCLUDED_OUS.getKey());

		if (setting == null || !StringUtils.hasLength(setting.getValue())) {
			return new HashSet<>();
		}

		String[] uuids = setting.getValue().split(",");

		return new HashSet<>(Arrays.asList(uuids));
	}

	@CacheEvict(value = {
		"SettingsCache-isAttestationOrgUnitSelectionOptIn",
		"SettingsCache-getExcludedOUs",
		"SettingsCache-automaticNiveauMappingEnabled",
		"SettingsCache-isNotificationTypeEnabled"
	}, allEntries = true)
	public void evictCache() {
		;
	}

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

	@Cacheable(value = "SettingsCache-isNotificationTypeEnabled")
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
			String prettyname = messageSource.getMessage(notificationType.getMessage(), null, Locale.of("da", "DK"));
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
		if (setting == null) {
			return 0;
		}
		return Integer.parseInt(setting.getValue());
	}

	public void setCurrentInstalledRank(int rank) {
		Setting setting = settingsDao.findByKey(Settings.SETTING_CURRENT_INSTALLED_RANK.getKey());
		if (setting == null) {
			setting = new Setting();
			setting.setKey(Settings.SETTING_CURRENT_INSTALLED_RANK.getKey());
		}
		setting.setValue(Integer.toString(rank));
		settingsDao.save(setting);
	}

	public List<RequestableBy> getRolerequestRequester() {
		Setting setting = settingsDao.findByKey(Settings.SETTING_ROLEREQUEST_REQUESTER.getKey());
		if (setting == null || setting.getValue() == null || setting.getValue().isEmpty()) {
			return List.of(RequestableBy.NONE);
		}
		String[] split = setting.getValue().split(",");
		List<RequestableBy> result = new ArrayList<>();
		for (String s : split) {
			result.add(RequestableBy.valueOf(s));
		}
		return result;
	}

	public void setRolerequestRequester(List<RequestableBy> requesterSetting) {
		Setting setting = settingsDao.findByKey(Settings.SETTING_ROLEREQUEST_REQUESTER.getKey());
		if (setting == null) {
			setting = new Setting();
			setting.setKey(Settings.SETTING_ROLEREQUEST_REQUESTER.getKey());
		}
		setting.setValue(requesterSetting.stream().map(Enum::name).collect(Collectors.joining(",")));
		settingsDao.save(setting);

	}

	public List<ApprovableBy> getRolerequestApprover() {
		Setting setting = settingsDao.findByKey(Settings.SETTING_ROLEREQUEST_APPROVER.getKey());
		if (setting == null || !StringUtils.hasLength(setting.getValue())) {
			return List.of();
		}
		List<ApprovableBy> result = new ArrayList<>();
		String[] split = setting.getValue().split(",");
		for (String s : split) {
			result.add(ApprovableBy.valueOf(s));
		}
		return result;
	}

	public void setRolerequestApprover(List<ApprovableBy> approverSetting) {
		Setting setting = settingsDao.findByKey(Settings.SETTING_ROLEREQUEST_APPROVER.getKey());
		if (setting == null) {
			setting = new Setting();
			setting.setKey(Settings.SETTING_ROLEREQUEST_APPROVER.getKey());
		}
		setting.setValue(approverSetting.stream().map(Enum::name).collect(Collectors.joining(",")));
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

	public void setRoleRequestApproverEmails(Map<ApprovableBy, String> approverEmails) {
		Setting setting = settingsDao.findByKey(Settings.SETTING_ROLEREQUEST_APPROVER_EMAIL.getKey());
		if (setting == null) {
			setting = new Setting();
			setting.setKey(Settings.SETTING_ROLEREQUEST_APPROVER_EMAIL.getKey());
		}

		// Convert map to delimited string format: "ADMINISTRATOR:email1|AUTHRESPONSIBLE:email2"
		StringBuilder sb = new StringBuilder();
		if (approverEmails != null) {
			for (Map.Entry<ApprovableBy, String> entry : approverEmails.entrySet()) {
				if (entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
					if (sb.length() > 0) {
						sb.append("|");
					}
					sb.append(entry.getKey().name()).append(":").append(entry.getValue().trim());
				}
			}
		}

		setting.setValue(sb.toString());
		settingsDao.save(setting);
	}

	public Map<ApprovableBy, String> getRoleRequestApproverEmails() {
		Setting setting = settingsDao.findByKey(Settings.SETTING_ROLEREQUEST_APPROVER_EMAIL.getKey());
		Map<ApprovableBy, String> result = new HashMap<>();
		if (setting != null && setting.getValue() != null && !setting.getValue().trim().isEmpty()) {
			String value = setting.getValue().trim();

			// Split ved semikolon for at få individuelle godkender-par
			// Forventet format: "AUTHRESPONSIBLE:email1@example.com;MANAGERORSUBSTITUTE:email2@example.com"
			String[] pairs = value.split(";");

			for (String pair : pairs) {
				// Split hvert par ved kolon for at adskille type og email
				String[] parts = pair.split(":", 2);
				if (parts.length == 2) {
					try {
						ApprovableBy approver = ApprovableBy.valueOf(parts[0].trim());
						String email = parts[1].trim();
						// Tilføj kun hvis email ikke er tom
						if (!email.isEmpty()) {
							result.put(approver, email);
						}
					} catch (IllegalArgumentException e) {
						log.warn("Invalid ApprovableBy enum value found in settings: {}", parts[0]);
					}
				}
			}
		}

		return result;
	}

	public boolean getOnlyRecommendRoles() {
		return isKeyEnabled(Settings.SETTING_ROLEREQUEST_ONLY_RECOMMENDED_ROLES.getKey());
	}

	public void setOnlyRecommendRoles(boolean enabled) {
		setKeyEnabled(enabled, Settings.SETTING_ROLEREQUEST_ONLY_RECOMMENDED_ROLES.getKey());
	}


	@Cacheable(value = "SettingsCache-isAttestationOrgUnitSelectionOptIn")
	public boolean isAttestationOrgUnitSelectionOptIn() {
		return getBooleanWithDefault(Settings.SETTING_ATTESTATION_ORGUNIT_OPTIN.getKey(), false);
	}

	public void setAttestationOrgUnitSelectionOptIn(boolean optinEnabled) {
		setKeyEnabled(optinEnabled, Settings.SETTING_ATTESTATION_ORGUNIT_OPTIN.getKey());
	}


	public void setZonedDateTime(final String kitosKey, final ZonedDateTime zonedDateTime) {
		Setting setting = settingsDao.findByKey(kitosKey);
		if (setting == null) {
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

	public boolean isAttestationDescriptionRequired() {
		return getKeyOrDefault(Settings.SETTING_SCHEDULED_ATTESTATION_DESCRIPTION_REQUIRED.getKey(), true);
	}

	public boolean isAttestationHideDescription() {
		return isKeyEnabled(Settings.SETTING_SCHEDULED_ATTESTATION_HIDE_DESCRIPTION.getKey());
	}

	public void setAttestationDescriptionRequired(boolean enabled) {
		boolean changed = setKeyEnabled(enabled, Settings.SETTING_SCHEDULED_ATTESTATION_DESCRIPTION_REQUIRED.getKey());
		if (changed) {
			AuditLogContextHolder.getContext().addArgument("Ny værdi", (enabled ? "true" : "false"));
			auditLogger.logSetting(settingsDao.findByKey(Settings.SETTING_SCHEDULED_ATTESTATION_DESCRIPTION_REQUIRED.getKey()), null, null, getPrettyName(Settings.SETTING_SCHEDULED_ATTESTATION_DESCRIPTION_REQUIRED));
			AuditLogContextHolder.clearContext();
		}
	}

	public void setAttestationHideDescription(boolean enabled) {
		boolean changed = setKeyEnabled(enabled, Settings.SETTING_SCHEDULED_ATTESTATION_HIDE_DESCRIPTION.getKey());
		if (changed) {
			AuditLogContextHolder.getContext().addArgument("Ny værdi", (enabled ? "true" : "false"));
			auditLogger.logSetting(settingsDao.findByKey(Settings.SETTING_SCHEDULED_ATTESTATION_HIDE_DESCRIPTION.getKey()), null, null, getPrettyName(Settings.SETTING_SCHEDULED_ATTESTATION_HIDE_DESCRIPTION));
			AuditLogContextHolder.clearContext();
		}
	}

	public Map<Integer, OrgUnitLevel> getNiveauMapping() {
		Map<Integer, OrgUnitLevel> mappings = new HashMap<>();

		List<Setting> niveauSettings = settingsDao.findByKeyStartingWith(Settings.SETTING_OU_TO_NIVEAU.getKey() + "[");

		for (Setting setting : niveauSettings) {
			try {
				// Extract depth from key
				String key = setting.getKey();
				String depthStr = key.substring(
						key.indexOf("[") + 1,
						key.indexOf("]")
				);

				Integer depth = Integer.parseInt(depthStr);
				OrgUnitLevel niveau = OrgUnitLevel.valueOf(setting.getValue());

				mappings.put(depth, niveau);
			} catch (Exception e) {
				// Does not happen realistically, we keep it
				log.error("Invalid niveau mapping setting: {} = {}", setting.getKey(), setting.getValue(), e);
			}
		}

		return mappings;
	}

	public void setNiveauMapping(Map<Integer, OrgUnitLevel> depthToNiveauMappings) {
		// Delete all previous mappings
		clearExistingNiveauMappings();

		// Save new mappings
		depthToNiveauMappings.forEach((depth, niveau) -> {
			if (niveau != null) {
				Setting setting = new Setting();
				setting.setKey(Settings.SETTING_OU_TO_NIVEAU.getKey() + "[" + depth + "]");
				setting.setValue(niveau.name());
				settingsDao.save(setting);
			}
		});
	}

	public void clearExistingNiveauMappings() {
		List<Setting> existingMappings = settingsDao.findByKeyStartingWith(Settings.SETTING_OU_TO_NIVEAU.getKey() + "[");
		settingsDao.deleteAll(existingMappings);
	}

	@Cacheable(value = "SettingsCache-automaticNiveauMappingEnabled")
	public boolean isAutomaticNiveauMappingEnabled() {
		return isKeyEnabled(Settings.SETTING_ALLOW_AUTOMATIC_OU_NIVEAU_MAPPING.getKey());
	}

	public void setAutomaticNiveauMapping(boolean enabled) {
		boolean changed = setKeyEnabled(enabled, Settings.SETTING_ALLOW_AUTOMATIC_OU_NIVEAU_MAPPING.getKey());
		if (changed) {
			AuditLogContextHolder.getContext().addArgument("Ny værdi", (enabled ? "true" : "false"));
			auditLogger.logSetting(settingsDao.findByKey(Settings.SETTING_CASE_NUMBER_ENABLED.getKey()), null, null, getPrettyName(Settings.SETTING_ALLOW_AUTOMATIC_OU_NIVEAU_MAPPING));
			AuditLogContextHolder.clearContext();
		}
	}

	public Integer getDataSeedVersion() {
		String dataSeedVersionKey = "DATA_SEED_VERSION";
		try {
			final Setting dataSeedVersion = getByKey(dataSeedVersionKey);
			String dataSeedVersionString = dataSeedVersion.getValue();
			return Integer.parseInt(dataSeedVersionString);
		} catch (Exception e) {
			// If the version could not be retrieved for any reason, assume it doesn't exist
			return 0;
		}
	}

	public Integer setDataSeedVersion(Integer version) {
		String dataSeedVersionKey = "DATA_SEED_VERSION";
		Setting dataSeedVersion = getByKey(dataSeedVersionKey);
		if (dataSeedVersion == null) {
			dataSeedVersion = new Setting();
			dataSeedVersion.setKey(dataSeedVersionKey);
		}
		dataSeedVersion.setValue(version.toString());
		settingsDao.save(dataSeedVersion);
		return version;
	}

	public boolean isDevDataSeeded() {
		String key = "DEV_DATA_SEEDED";
		try {
			Setting setting = getByKey(key);
			return setting != null && Boolean.parseBoolean(setting.getValue());
		} catch (Exception e) {
			return false;
		}
	}

	public void setDevDataSeeded(boolean seeded) {
		String key = "DEV_DATA_SEEDED";
		Setting setting = getByKey(key);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(key);
		}
		setting.setValue(Boolean.toString(seeded));
		settingsDao.save(setting);
	}

	public LocalDateTime getFirstManualITSystemRun() {
		Setting setting = settingsDao.findByKey(Settings.SETTING_FIRST_MANUAL_IT_SYSTEM_RUN.getKey());
		if (setting == null) {
			return null;
		}

		return LocalDateTime.parse(setting.getValue());
	}

	public void setFirstManualITSystemRun(LocalDateTime firstRun) {
		Setting setting = settingsDao.findByKey(Settings.SETTING_FIRST_MANUAL_IT_SYSTEM_RUN.getKey());
		if (setting == null) {
			setting = new Setting();
			setting.setKey(Settings.SETTING_FIRST_MANUAL_IT_SYSTEM_RUN.getKey());
		}

		setting.setValue(firstRun.toString());
		settingsDao.save(setting);
	}


}

package dk.digitalidentity.rc.service;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.dao.SettingsDao;
import dk.digitalidentity.rc.dao.model.Setting;
import dk.digitalidentity.rc.dao.model.enums.CheckupIntervalEnum;
import dk.digitalidentity.rc.service.model.OrganisationEventAction;
import dk.digitalidentity.rc.service.model.RequestApproveManagerAction;

@Service
public class SettingsService {
	private static final String SETTING_ORGANISATION_EVENTS_ENABLED = "OrganisationEventsEnabled";
	private static final String SETTING_OU_NEW_MANAGER_ACTION = "OuNewManagerAction";
	private static final String SETTING_OU_NEW_PARENT_ACTION = "OuNewParentAction";
	private static final String SETTING_USER_NEW_POSITION_ACTION = "UserNewPositionAction";
	private static final String SETTING_IT_SYSTEM_MARKUP_ENABLED = "ItSystemMarkup";
	private static final String SETTING_REQUEST_APPROVE_ENABLED = "RequestApproveEnabled";
	private static final String SETTING_REQUEST_APPROVE_MANAGER_ACTION = "RequestApproveManagerAction";
	private static final String SETTING_REQUEST_APPROVE_SERVICEDESK_EMAIL = "RequestApproveServicedeskEmail";
	private static final String SETTING_SCHEDULED_ATTESTATION_ENABLED = "ScheduledAttestationEnabled";
	private static final String SETTING_SCHEDULED_ATTESTATION_INTERVAL = "ScheduledAttestationInterval";
	private static final String SETTING_SCHEDULED_ATTESTATION_INTERVAL_SENSITIVE = "ScheduledAttestationIntervalSensitive";
	private static final String SETTING_SCHEDULED_ATTESTATION_DAY_IN_MONTH = "ScheduledAttestationDayInMonth";
	private static final String SETTING_SCHEDULED_ATTESTATION_FILTER = "ScheduledAttestationFilter";
	private static final String SETTING_SCHEDULED_ATTESTATION_LAST_RUN = "ScheduledAttestationLastRun";
	private static final String SETTING_IT_SYSTEM_CHANGE_EMAIL = "ItSystemChangeEmail";
	private static final String SETTING_REMOVAL_OF_UNIT_ROLES_EMAIL = "RemovalOfUnitRolesEmail";
	private static final String SETTING_REMINDER_COUNT = "ReminderCount";
	private static final String SETTING_REMINDER_INTERVAL = "ReminderInterval";
	private static final String SETTING_DAYS_BEFORE_DEADLINE = "DaysBeforeDeadline";
	private static final String SETTING_EMAIL_AFTER_REMINDERS = "EmailAfterReminders";

	@Autowired
	private SettingsDao settingsDao;

	public boolean isOrganisationEventsEnabled() {
		return isKeyEnabled(SETTING_ORGANISATION_EVENTS_ENABLED);
	}

	public void setOrganisationEventsEnabled(boolean enabled) {
		setKeyEnabled(enabled, SETTING_ORGANISATION_EVENTS_ENABLED);
	}
	
	public boolean isItSystemMarkupEnabled() {
		return isKeyEnabled(SETTING_IT_SYSTEM_MARKUP_ENABLED);
	}
	
	public void setItSystemMarkupEnabled(boolean enabled) {
		setKeyEnabled(enabled, SETTING_IT_SYSTEM_MARKUP_ENABLED);
	}
	
	public boolean isRequestApproveEnabled() {
		return isKeyEnabled(SETTING_REQUEST_APPROVE_ENABLED);
	}
	
	public void setRequestApproveEnabled(boolean enabled) {
		setKeyEnabled(enabled, SETTING_REQUEST_APPROVE_ENABLED);
	}
	
	public RequestApproveManagerAction getRequestApproveManagerAction() {
		String value = getKeyWithDefault(SETTING_REQUEST_APPROVE_MANAGER_ACTION, RequestApproveManagerAction.NONE.toString());
		
		return RequestApproveManagerAction.valueOf(value);
	}
	
	public void setRequestApproveManagerAction(RequestApproveManagerAction action) {
		Setting setting = settingsDao.getByKey(SETTING_REQUEST_APPROVE_MANAGER_ACTION);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_REQUEST_APPROVE_MANAGER_ACTION);
		}
		
		setting.setValue(action.toString());
		settingsDao.save(setting);
	}
	
	public String getRequestApproveServicedeskEmail() {
		Setting setting = settingsDao.getByKey(SETTING_REQUEST_APPROVE_SERVICEDESK_EMAIL);
		if (setting == null) {
			return "";
		}

		return setting.getValue();
	}
	
	public void setRequestApproveServicedeskEmail(String email) {
		Setting setting = settingsDao.getByKey(SETTING_REQUEST_APPROVE_SERVICEDESK_EMAIL);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_REQUEST_APPROVE_SERVICEDESK_EMAIL);
		}
		
		setting.setValue(email);
		settingsDao.save(setting);
	}
	
	public String getRemovalOfUnitRolesEmail() {
		Setting setting = settingsDao.getByKey(SETTING_REMOVAL_OF_UNIT_ROLES_EMAIL);
		if (setting == null) {
			return "";
		}

		return setting.getValue();
	}
	
	public void setRemovalOfUnitRolesEmail(String email) {
		Setting setting = settingsDao.getByKey(SETTING_REMOVAL_OF_UNIT_ROLES_EMAIL);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_REMOVAL_OF_UNIT_ROLES_EMAIL);
		}
		
		setting.setValue(email);
		settingsDao.save(setting);
	}
	
	public int getReminderCount() {
		Setting setting = settingsDao.getByKey(SETTING_REMINDER_COUNT);
		if (setting == null || StringUtils.isEmpty(setting.getValue())) {
			return 2;
		}
		
		int count = 2;
		try {
			count = Integer.parseInt(setting.getValue());
		}
		catch (Exception ex) {
			; // ignore
		}
		

		return count;
	}
	
	public void setReminderCount(long count) {
		Setting setting = settingsDao.getByKey(SETTING_REMINDER_COUNT);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_REMINDER_COUNT);
		}
		
		setting.setValue(Long.toString(count));
		settingsDao.save(setting);
	}
	
	public int getReminderInterval() {
		Setting setting = settingsDao.getByKey(SETTING_REMINDER_INTERVAL);
		if (setting == null || StringUtils.isEmpty(setting.getValue())) {
			return 7;
		}
		
		int interval = 7;
		try {
			interval = Integer.parseInt(setting.getValue());
		}
		catch (Exception ex) {
			; // ignore
		}
		
		return interval;
	}
	
	public void setReminderInterval(long interval) {
		Setting setting = settingsDao.getByKey(SETTING_REMINDER_INTERVAL);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_REMINDER_INTERVAL);
		}
		
		setting.setValue(Long.toString(interval));
		settingsDao.save(setting);
	}
	
	public int getDaysBeforeDeadline() {
		Setting setting = settingsDao.getByKey(SETTING_DAYS_BEFORE_DEADLINE);
		if (setting == null || StringUtils.isEmpty(setting.getValue())) {
			return 7;
		}
		
		int daysBeforeDeadline = 7;
		try {
			daysBeforeDeadline = Integer.parseInt(setting.getValue());
		}
		catch (Exception ex) {
			; // ignore
		}
		
		return daysBeforeDeadline;
	}
	
	public void setDaysBeforeDeadline(long days) {
		Setting setting = settingsDao.getByKey(SETTING_DAYS_BEFORE_DEADLINE);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_DAYS_BEFORE_DEADLINE);
		}
		
		setting.setValue(Long.toString(days));
		settingsDao.save(setting);
	}
	
	public String getEmailAfterReminders() {
		Setting setting = settingsDao.getByKey(SETTING_EMAIL_AFTER_REMINDERS);
		if (setting == null) {
			return "";
		}

		return setting.getValue();
	}
	
	public void setEmailAfterReminders(String email) {
		Setting setting = settingsDao.getByKey(SETTING_EMAIL_AFTER_REMINDERS);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_EMAIL_AFTER_REMINDERS);
		}
		
		setting.setValue(email);
		settingsDao.save(setting);
	}
	
	public Set<String> getScheduledAttestationFilter() {
		Setting setting = settingsDao.getByKey(SETTING_SCHEDULED_ATTESTATION_FILTER);
		if (setting == null || StringUtils.isEmpty(setting.getValue())) {
			return new HashSet<>();
		}
		
		String[] tokens = setting.getValue().split(",");
		
		return new HashSet<String>(Arrays.asList(tokens));
	}
	
	public void setScheduledAttestationDayInMonth(long dayInMonth) {
		Setting setting = settingsDao.getByKey(SETTING_SCHEDULED_ATTESTATION_DAY_IN_MONTH);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_SCHEDULED_ATTESTATION_DAY_IN_MONTH);
		}
		
		// panic filter
		if (dayInMonth < 1 || dayInMonth > 28) {
			dayInMonth = 10;
		}

		setting.setValue(Long.toString(dayInMonth));
		settingsDao.save(setting);
	}
	
	public long getScheduledAttestationDayInMonth() {
		Setting setting = settingsDao.getByKey(SETTING_SCHEDULED_ATTESTATION_DAY_IN_MONTH);
		if (setting == null || StringUtils.isEmpty(setting.getValue())) {
			return 10;
		}
		
		long dayInMonth = 10;
		try {
			dayInMonth = Long.parseLong(setting.getValue());
		}
		catch (Exception ex) {
			; // ignore
		}

		if (dayInMonth < 1 || dayInMonth > 28) {
			dayInMonth = 10;
		}

		return dayInMonth;
	}
	
	public void setScheduledAttestationFilter(Set<String> filter) {
		Setting setting = settingsDao.getByKey(SETTING_SCHEDULED_ATTESTATION_FILTER);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_SCHEDULED_ATTESTATION_FILTER);
		}
		
		setting.setValue(String.join(",", filter));
		settingsDao.save(setting);
	}

	public OrganisationEventAction getOuNewManagerAction() {
		String value = getKeyWithDefault(SETTING_OU_NEW_MANAGER_ACTION, OrganisationEventAction.RIGHTS_KEPT.toString());
		
		return OrganisationEventAction.valueOf(value);
	}

	public OrganisationEventAction getOuNewParentAction() {
		String value = getKeyWithDefault(SETTING_OU_NEW_PARENT_ACTION, OrganisationEventAction.RIGHTS_KEPT.toString());
		
		return OrganisationEventAction.valueOf(value);
	}

	public OrganisationEventAction getUserNewPositionAction() {
		String value = getKeyWithDefault(SETTING_USER_NEW_POSITION_ACTION, OrganisationEventAction.RIGHTS_KEPT.toString());
		
		return OrganisationEventAction.valueOf(value);
	}

	public void setOuNewManagerAction(OrganisationEventAction action) {
		Setting setting = settingsDao.getByKey(SETTING_OU_NEW_MANAGER_ACTION);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_OU_NEW_MANAGER_ACTION);
		}
		
		setting.setValue(action.toString());
		settingsDao.save(setting);
	}

	public void setOuNewParentAction(OrganisationEventAction action) {
		Setting setting = settingsDao.getByKey(SETTING_OU_NEW_PARENT_ACTION);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_OU_NEW_PARENT_ACTION);
		}
		
		setting.setValue(action.toString());
		settingsDao.save(setting);
	}

	public void setUserNewPositionAction(OrganisationEventAction action) {
		Setting setting = settingsDao.getByKey(SETTING_USER_NEW_POSITION_ACTION);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_USER_NEW_POSITION_ACTION);
		}
		
		setting.setValue(action.toString());
		settingsDao.save(setting);
	}

	/// helper methods

	private String getKeyWithDefault(String key, String defaultValue) {
		Setting setting = settingsDao.getByKey(key);
		if (setting != null) {
			return setting.getValue();
		}
		
		return defaultValue;
	}

	private boolean isKeyEnabled(String key) {
		Setting setting = settingsDao.getByKey(key);
		if (setting != null) {
			if ("true".equals(setting.getValue())) {
				return true;
			}
		}

		return false;
	}
	
	private void setKeyEnabled(boolean enabled, String key) {
		Setting setting = settingsDao.getByKey(key);
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
		Setting setting = settingsDao.getByKey(SETTING_SCHEDULED_ATTESTATION_INTERVAL);
		if (setting == null) {
			return CheckupIntervalEnum.EVERY_HALF_YEAR;
		}

		return CheckupIntervalEnum.valueOf(setting.getValue());
	}
	
	public void setScheduledAttestationInterval(CheckupIntervalEnum interval) {
		Setting setting = settingsDao.getByKey(SETTING_SCHEDULED_ATTESTATION_INTERVAL);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_SCHEDULED_ATTESTATION_INTERVAL);
		}
		
		setting.setValue(interval.toString());
		settingsDao.save(setting);
	}
	
	public CheckupIntervalEnum getScheduledAttestationIntervalSensitive() {
		Setting setting = settingsDao.getByKey(SETTING_SCHEDULED_ATTESTATION_INTERVAL_SENSITIVE);
		if (setting == null) {
			return CheckupIntervalEnum.QUARTERLY;
		}

		return CheckupIntervalEnum.valueOf(setting.getValue());
	}

	public void setScheduledAttestationIntervalSensitive(CheckupIntervalEnum interval) {
		Setting setting = settingsDao.getByKey(SETTING_SCHEDULED_ATTESTATION_INTERVAL_SENSITIVE);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_SCHEDULED_ATTESTATION_INTERVAL_SENSITIVE);
		}
		
		setting.setValue(interval.toString());
		settingsDao.save(setting);
	}
	
	public Date getScheduledAttestationLastRun() {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");

		try {
			Setting setting = settingsDao.getByKey(SETTING_SCHEDULED_ATTESTATION_LAST_RUN);
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

		Setting setting = settingsDao.getByKey(SETTING_SCHEDULED_ATTESTATION_LAST_RUN);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_SCHEDULED_ATTESTATION_LAST_RUN);
		}
		
		setting.setValue(dateString);
		settingsDao.save(setting);
	}

	public String getItSystemChangeEmail() {
		Setting setting = settingsDao.getByKey(SETTING_IT_SYSTEM_CHANGE_EMAIL);
		if (setting == null) {
			return "";
		}

		return setting.getValue();
	}

	public void setItSystemChangeEmail(String email) {
		Setting setting = settingsDao.getByKey(SETTING_IT_SYSTEM_CHANGE_EMAIL);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(SETTING_IT_SYSTEM_CHANGE_EMAIL);
		}
		
		setting.setValue(email);
		settingsDao.save(setting);
	}
}
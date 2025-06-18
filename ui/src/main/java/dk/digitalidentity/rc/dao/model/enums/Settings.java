package dk.digitalidentity.rc.dao.model.enums;

import lombok.Getter;

@Getter
public enum Settings {
    SETTING_REQUEST_APPROVE_ENABLED("RequestApproveEnabled", "html.setting.requestapprove.enabled"),
    SETTING_REQUEST_APPROVE_SERVICEDESK_EMAIL("RequestApproveServicedeskEmail", "html.setting.requestapprove.servicedesk"),
    SETTING_SCHEDULED_ATTESTATION_ENABLED("ScheduledAttestationEnabled", "html.setting.attestation.scheduled.enabled"),
    SETTING_SCHEDULED_ATTESTATION_INTERVAL("ScheduledAttestationInterval", "html.setting.attestation.scheduled.interval"),
    SETTING_SCHEDULED_ATTESTATION_FILTER_OLD("ScheduledAttestationFilter", null),
    SETTING_SCHEDULED_ATTESTATION_EXCEPTED_ORG_UNITS("ScheduledAttestationExceptedOrgUnits", "html.setting.attestation.scheduled.filter"),
    SETTING_SCHEDULED_ATTESTATION_LAST_RUN("ScheduledAttestationLastRun", null),
    SETTING_IT_SYSTEM_CHANGE_EMAIL("ItSystemChangeEmail", "html.setting.itsystem.change.email"),
    SETTING_ATTESTATIONCHANGE_EMAIL("RemovalOfUnitRolesEmail", "html.setting.remove.role.email"),
    SETTING_AD_ATTESTATION("AttestationADEnabled", "html.setting.attestation.scheduled.ad.enabled"),
    SETTING_ALLOW_CHANGE_REQUEST_ATTESTATION("AttestationAllowChanges", "html.setting.attestation.changes.enabled"),
    SETTING_RUN_CICS("RunCics", null),
    SETTING_IT_SYSTEM_DEFAULT_HIDDEN_ENABLED("ItSystemHiddenByDefault", "html.setting.kombit.itsystems_hidden_by_default.enabled"),
    SETTING_FIRST_ATTESTATION_DATE("FirstAttestationDate", "html.setting.attestation.scheduled.firstAttestation"),
    SETTING_MITID_ERHVERV_MIGRATION_PERFORMED("MitIDErhvervMigrationPerformed", null),
    SETTING_BLOCK_ALL_EMAIL_TRANSMISSIONS("BlockAllEmailTransmissions", null),
    SETTING_EMAIL_QUEUE_LIMIT("EmailQueueLimit", null),
    SETTING_CURRENT_INSTALLED_RANK("currentInstalledRank", null),
    SETTING_DONT_SEND_MAIL_TO_MANAGER("DontSendMailToMangerEnabled", null),
	SETTING_VIKAR_REGEX("VikarRegEx", null),
    SETTING_ROLEREQUEST_REQUESTER("allowedrequesters", null),
    SETTING_ROLEREQUEST_APPROVER("allowedrapprovers", null),
    SETTING_ROLEREQUEST_REASON("requestreason", null),
    SETTING_ROLEREQUEST_ONLY_RECOMMENDED_ROLES("onlyrecommendedroles", null),
    SETTING_CASE_NUMBER_ENABLED("caseNumberEnabled", "html.setting.caseNumber.enabled"),
    SETTING_EXCLUDED_OUS("ExcludedOUs", "html.setting.pickou");


    private String key;
    private String message;

    private Settings(String key, String message) {
        this.key = key;
        this.message = message;
    }
}

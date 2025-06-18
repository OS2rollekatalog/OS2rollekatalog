package dk.digitalidentity.rc.rolerequest.model.enums;

import lombok.Getter;

import java.util.List;

@Getter
public enum ApproverOption {
    INHERIT("html.enum.settings.approver.option.inherit",
		"html.enum.settings.approver.option.inherit.description",
		List.of()),
    AUTOMATIC("html.enum.settings.approver.option.automatic",
		"html.enum.settings.approver.option.automatic.description",
		List.of(ApprovableBy.values())),
    AUTHORIZEDMANAGERORAUTHRESPONSIBLE("html.enum.settings.approver.option.authorizedmanagerauthresponsible",
		"html.enum.settings.approver.option.authorizedmanagerauthresponsible.description",
		List.of(ApprovableBy.ADMINISTRATOR,ApprovableBy.MANAGERORSUBSTITUTE, ApprovableBy.AUTHRESPONSIBLE, ApprovableBy.AUTHORIZED)),
    MANAGERORAUTHRESPONSIBLE("html.enum.settings.approver.option.managerauthresponsible",
		"html.enum.settings.approver.option.managerauthresponsible.description",
		List.of(ApprovableBy.ADMINISTRATOR, ApprovableBy.MANAGERORSUBSTITUTE, ApprovableBy.AUTHRESPONSIBLE)),
    AUTHORIZEDONLY("html.enum.settings.approver.option.authorizedonly",
		"html.enum.settings.approver.option.authorizedonly.description",
		List.of(ApprovableBy.ADMINISTRATOR, ApprovableBy.AUTHORIZED)),
    ADMINONLY("html.enum.settings.approver.option.adminonly",
		"html.enum.settings.approver.option.adminonly.description",
		List.of(ApprovableBy.ADMINISTRATOR)),
    SYSTEMRESPONSIBLE("html.enum.settings.approver.option.systemresponsible",
		"html.enum.settings.approver.option.systemresponsible.description",
		List.of(ApprovableBy.ADMINISTRATOR, ApprovableBy.SYSTEMRESPONSIBLE));

    private final String message;
	private final String description;
    private final List<ApprovableBy> approverPermissions;

    ApproverOption(String message, String description, List<ApprovableBy> approverPermissions) {
        this.message = message;
		this.description = description;
		this.approverPermissions = approverPermissions;
    }
}

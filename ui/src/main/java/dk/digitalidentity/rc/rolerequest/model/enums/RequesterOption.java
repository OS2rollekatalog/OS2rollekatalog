package dk.digitalidentity.rc.rolerequest.model.enums;

import lombok.Getter;

import java.util.List;

@Getter
public enum RequesterOption {
	INHERIT("html.enum.settings.requester.option.inherit",
		"html.enum.settings.requester.option.inherit.description",
			List.of()),

	NONE("html.enum.settings.requester.option.none",
		"html.enum.settings.requester.option.none.description",
			List.of()),

	EMPLOYEESONLY("html.enum.settings.requester.option.employee",
		"html.enum.settings.requester.option.employee.description",
			List.of(RequestableBy.EMPLOYEE, RequestableBy.ADMIN)),

	ALL("html.enum.settings.requester.option.all",
		"html.enum.settings.requester.option.all.description",
			List.of(RequestableBy.EMPLOYEE, RequestableBy.AUTHRESPONSIBLE, RequestableBy.AUTHORIZED, RequestableBy.MANAGERORSUBSTITUTE, RequestableBy.ADMIN)),

	EMPLOYEESMANAGERSANDAUTHRESPONSIBLE("html.enum.settings.requester.option.employyemanagerresponsible",
		"html.enum.settings.requester.option.employyemanagerresponsible.description",
			List.of(RequestableBy.MANAGERORSUBSTITUTE, RequestableBy.AUTHRESPONSIBLE, RequestableBy.ADMIN)),

	AUTHORIZEDMANAGERSANDAUTHRESPONSIBLE("html.enum.settings.requester.option.authorizedmanagerresponsible",
		"html.enum.settings.requester.option.authorizedmanagerresponsible.description",
			List.of(RequestableBy.AUTHRESPONSIBLE, RequestableBy.AUTHRESPONSIBLE, RequestableBy.ADMIN)),

	AUTHORIZEDONLY("html.enum.settings.requester.option.authorized",
		"html.enum.settings.requester.option.authorized.description",
			List.of(RequestableBy.AUTHORIZED, RequestableBy.ADMIN)),

	MANAGERANDAUTHRESPONSIBLE("html.enum.settings.requester.option.managerresponsible",
		"html.enum.settings.requester.option.managerresponsible.description",
			List.of(RequestableBy.AUTHRESPONSIBLE, RequestableBy.MANAGERORSUBSTITUTE, RequestableBy.ADMIN));

	private final String message;
	private final String description;
	private final List<RequestableBy> requestPermissions;

	RequesterOption(String message, String description, List<RequestableBy> requestPermissions) {
		this.message = message;
		this.description = description;
		this.requestPermissions = requestPermissions;
	}

}

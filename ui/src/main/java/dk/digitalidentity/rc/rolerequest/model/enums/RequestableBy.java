package dk.digitalidentity.rc.rolerequest.model.enums;

import lombok.Getter;

@Getter
public enum RequestableBy {
	EMPLOYEE,
	MANAGERORSUBSTITUTE,
	AUTHRESPONSIBLE,
	AUTHORIZED,
	ADMIN
}

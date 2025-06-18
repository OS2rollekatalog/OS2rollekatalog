package dk.digitalidentity.rc.rolerequest.model.enums;

import lombok.Getter;

@Getter
public enum ApprovableBy {
    AUTOMATIC("html.enum.sttings.approvableby.automatic"),
    AUTHRESPONSIBLE("html.enum.sttings.approvableby.authresponsible"),
    MANAGERORSUBSTITUTE("html.enum.sttings.approvableby.manager"),
    AUTHORIZED("html.enum.sttings.approvableby.authorized"),
    ADMINISTRATOR("html.enum.sttings.approvableby.admin"),
    SYSTEMRESPONSIBLE("html.enum.sttings.approvableby.systemresponsible");

	private String message;

	private ApprovableBy(String message) {
		this.message = message;
	}

}

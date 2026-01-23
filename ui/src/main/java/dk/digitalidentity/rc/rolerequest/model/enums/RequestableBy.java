package dk.digitalidentity.rc.rolerequest.model.enums;

import dk.digitalidentity.rc.util.ApplicationContextProvider;
import lombok.Getter;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

@Getter
public enum RequestableBy {
	// IMPORTANT! these names must not overlap, so contains in SQL returns false positives.
	INHERIT("html.enum.sttings.requestableby.inherit"),
	EMPLOYEE("html.enum.sttings.requestableby.employee"),
	MANAGERORSUBSTITUTE("html.enum.sttings.requestableby.manager"),
	AUTHRESPONSIBLE("html.enum.sttings.requestableby.authresponsible"),
	AUTHORIZED("html.enum.sttings.requestableby.authorized"),
	ADMIN("html.enum.sttings.requestableby.admin"),
	NONE("html.enum.sttings.requestableby.none");

	private String message;
	private RequestableBy(String message) {
		this.message = message;
	}

	public String getDisplayName() {
		MessageSource messageSource = ApplicationContextProvider.getBean(MessageSource.class);
		return messageSource.getMessage(this.message, null, LocaleContextHolder.getLocale());
	}
}

package dk.digitalidentity.rc.rolerequest.model.enums;

import dk.digitalidentity.rc.util.ApplicationContextProvider;
import lombok.Getter;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

@Getter
public enum ApprovableBy {
	INHERIT("html.enum.sttings.approvableby.inherit"),
    AUTOMATIC("html.enum.sttings.approvableby.automatic"),
    AUTHRESPONSIBLE("html.enum.sttings.approvableby.authresponsible"),
    MANAGERORSUBSTITUTE("html.enum.sttings.approvableby.manager"),
    AUTHORIZED("html.enum.sttings.approvableby.authorized"),
    SYSTEMRESPONSIBLE("html.enum.sttings.approvableby.systemresponsible");

	private String message;
	private ApprovableBy(String message) {
		this.message = message;
	}

	public String getDisplayName() {
		MessageSource messageSource = ApplicationContextProvider.getBean(MessageSource.class);
		return messageSource.getMessage(this.message, null, LocaleContextHolder.getLocale());
	}
}

package dk.digitalidentity.rc.config.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Email {
	private boolean enabled = false;
	private String from = "no-reply@rollekatalog.dk";
	private String username;
	private String password;
	private String host;
}

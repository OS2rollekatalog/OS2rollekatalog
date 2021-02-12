package dk.digitalidentity.rc.config.model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Kombit {
	private boolean enabled = false;
	private String url = "https://admin.serviceplatformen.dk/stsadmin/xapi";
	private String domain;
	private String keystoreLocation;
	private String keystorePassword;
}

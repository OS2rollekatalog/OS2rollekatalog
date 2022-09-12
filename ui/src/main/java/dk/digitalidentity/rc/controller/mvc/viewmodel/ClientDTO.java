package dk.digitalidentity.rc.controller.mvc.viewmodel;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClientDTO {
	private long id;
	private String name;
	private String apiKey;
	private String accessRole;
	private String version;
	private boolean outdated;
}

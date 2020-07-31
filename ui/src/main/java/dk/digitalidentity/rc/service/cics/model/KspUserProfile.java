package dk.digitalidentity.rc.service.cics.model;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KspUserProfile {
	private String id;
	private String name;
	private String description;
	private List<String> users;
}

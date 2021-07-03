package dk.digitalidentity.rc.service.cics.model;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KspUser {
	private String userId;
	private String cpr;
	private List<String> authorisations;
}

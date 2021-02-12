package dk.digitalidentity.rc.service.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PrivilegeGroup {
	private Privilege privilege;
	private String cvr;
	private List<Constraint> constraints;
	
	public PrivilegeGroup() {
		constraints = new ArrayList<>();
	}
}

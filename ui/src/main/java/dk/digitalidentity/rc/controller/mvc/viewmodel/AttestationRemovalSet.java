package dk.digitalidentity.rc.controller.mvc.viewmodel;

import java.io.Serializable;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttestationRemovalSet implements Serializable {
	private static final long serialVersionUID = -6469549560744429017L;

	private List<Long> userRoles;
	private List<Long> roleGroups;
}

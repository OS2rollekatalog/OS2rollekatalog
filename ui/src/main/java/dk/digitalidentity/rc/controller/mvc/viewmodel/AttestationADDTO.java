package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AttestationADDTO {
	private User user;
	private boolean checked;
	
	public String getUserPositionName() {
		if (user != null && !user.getPositions().isEmpty()) {
			return user.getPositions().get(0).getName() + " i " + user.getPositions().get(0).getOrgUnit().getName();
		}

		return "";
	}
}

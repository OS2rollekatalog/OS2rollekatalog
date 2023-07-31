package dk.digitalidentity.rc.service.model;

import dk.digitalidentity.rc.dao.model.User;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserDeletedEvent {
	private User user;
	private String itSystemName;

	// can be more than one email separated with ;
	private String email;
}

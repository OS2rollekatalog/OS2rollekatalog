package dk.digitalidentity.rc.service.model;

import dk.digitalidentity.rc.dao.model.User;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserDeletedEvent {
	private User user;
	private String email;
	private String itSystemName;
}

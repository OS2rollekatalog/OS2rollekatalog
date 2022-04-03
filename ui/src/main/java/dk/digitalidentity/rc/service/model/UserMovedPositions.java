package dk.digitalidentity.rc.service.model;

import java.util.HashSet;
import java.util.Set;

import dk.digitalidentity.rc.dao.model.User;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserMovedPositions {
	private User user;
	private Set<MovedPostion> oldPositions;
	private Set<MovedPostion> newPositions;
	
	public UserMovedPositions() {
		oldPositions = new HashSet<>();
		newPositions = new HashSet<>();
		user = null;
	}
}

package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.User;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExceptedUsersDTO {
	private boolean excepted;
	private String id;
	private String name;
	private String userId;

	public ExceptedUsersDTO(User user, boolean excepted) {
		this.excepted = excepted;
		this.id = user.getUuid();
		this.name = user.getName();
		this.userId = user.getUserId();
	}
}

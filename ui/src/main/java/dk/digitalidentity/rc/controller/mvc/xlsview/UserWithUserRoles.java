package dk.digitalidentity.rc.controller.mvc.xlsview;

import java.util.List;

import dk.digitalidentity.rc.dao.model.User;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UserWithUserRoles {
	private User user;
	private List<Assignment> assignments;
}

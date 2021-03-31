package dk.digitalidentity.rc.controller.rest.model;

import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserListDTO {
	private String uuid;
	private String name;
	private String userId;
	private String positions;
	private boolean assigned;

	public UserListDTO(User user, String in) {
		this.uuid = user.getUuid();
		this.name = user.getName();
		this.userId = user.getUserId();

		in = " " + in + " ";
		StringBuilder builder = new StringBuilder();
		if (user.getPositions() != null) {
			for (Position position : user.getPositions()) {
				if (builder.length() > 0) {
					builder.append("<br>");
				}

				builder.append(position.getName());
				builder.append(in);
				builder.append(position.getOrgUnit().getName());
			}
		}

		this.positions = builder.toString();
	}
}

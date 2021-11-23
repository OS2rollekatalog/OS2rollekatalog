package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import lombok.Getter;

@Getter
public class UserListForm {
	private static final String operationsFormat = "<a href=\"%s/ui/users/manage/%s\"><em class=\"fa fa-fw fa-pencil\"></em></a>";
	private String operations;
	private String name;
	private String userId;
	private String positions;

	public UserListForm(User user, String contextPath, String in) {
		this.name = user.getName();
		this.userId = user.getUserId();
		this.operations = String.format(operationsFormat, contextPath, user.getUuid());

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

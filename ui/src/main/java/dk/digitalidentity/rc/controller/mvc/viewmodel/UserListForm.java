package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import lombok.Data;

@Data
public class UserListForm {
	private static final String operationsFormat = "<a href=\"%s/ui/users/view/%s\"><em class=\"fa fa-fw fa-search\"></em></a><a href=\"%s/ui/users/edit/%s\"><em class=\"fa fa-fw fa-pencil\"></em></a>";
	private static final String operationsFormatReadOnly = "<a href=\"%s/ui/users/view/%s\"><em class=\"fa fa-fw fa-search\"></em></a>";
	private String operations;
	private String name;
	private String userId;
	private String positions;

	public UserListForm(User user, String contextPath, String in, boolean readOnly) {
		this.name = user.getName();
		this.userId = user.getUserId();
		
		if (readOnly) {
			this.operations = String.format(operationsFormatReadOnly, contextPath, user.getUuid());
		}
		else {
			this.operations = String.format(operationsFormat, contextPath, user.getUuid(), contextPath, user.getUuid());
		}

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

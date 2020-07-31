package dk.digitalidentity.rc.controller.mvc.viewmodel;

import java.util.List;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OUTableForm {
	private static final String operationsFormat = "<a href=\"%s/ui/ous/view/%s?backRef=list\"><em class=\"fa fa-fw fa-search\"></em></a><a href=\"%s/ui/ous/edit/%s?backRef=list\"><em class=\"fa fa-fw fa-pencil\"></em></a>";
	private static final String nonEditableOperationsFormat = "<a href=\"%s/ui/ous/view/%s?backRef=list\"><em class=\"fa fa-fw fa-search\"></em></a>";
	private String operations;
	private String name;

	public OUTableForm(OrgUnit ou, String contextPath, List<String> ousThatCanBeEdited) {
		this.name = ou.getName();

		if (ousThatCanBeEdited == null || ousThatCanBeEdited.contains(ou.getUuid())) {
			this.operations = String.format(operationsFormat, contextPath, ou.getUuid(), contextPath, ou.getUuid());
		}
		else {
			this.operations = String.format(nonEditableOperationsFormat, contextPath, ou.getUuid());
		}

		StringBuilder parentBuilder = new StringBuilder();
		OrgUnit parent = ou.getParent();
		while (parent != null) {
			if (parent.getName().length() + parentBuilder.length() < 70) {
				parentBuilder.insert(0, " > ");
				parentBuilder.insert(0, parent.getName());
			}
			else {
				break;
			}

			parent = parent.getParent();
		}
		
		if (parentBuilder.length() > 0) {
			this.name = this.name + "<div class=\"ouParent\">" + parentBuilder.toString() + "</div>";
		}
	}
}

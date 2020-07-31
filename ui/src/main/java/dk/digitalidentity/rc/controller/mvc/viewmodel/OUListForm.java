package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OUListForm {
	private String id;
	private String parent;
	private String text;
	private boolean editable;
	private OUListFormState state = new OUListFormState();

	public OUListForm(OrgUnit ou, boolean editable) {
		this.text = ou.getName();
		this.id = ou.getUuid();
		this.parent = (ou.getParent() != null) ? ou.getParent().getUuid() : "#";
		this.editable = editable;
		
		if (ou.getLevel() != null) {
			switch (ou.getLevel()) {
				case LEVEL_1:
					text += " (niveau 1)";
					break;
				case LEVEL_2:
					text += " (niveau 2)";
					break;
				case LEVEL_3:
					text += " (niveau 3)";
					break;
				case LEVEL_4:
					text += " (niveau 4)";
					break;
				case NONE:
					break;
			}
		}
	}

	public OUListForm(HistoryOU ou) {
		this.text = ou.getOuName();
		this.id = ou.getOuUuid();
		this.parent = (ou.getOuParentUuid() != null) ? ou.getOuParentUuid() : "#";
		this.editable = false;
	}
}

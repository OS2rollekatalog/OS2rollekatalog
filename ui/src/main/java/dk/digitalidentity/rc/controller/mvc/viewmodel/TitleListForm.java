package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.Title;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TitleListForm {
	private String id;
	private String name;
	private TitleListFormState state = new TitleListFormState();
	private boolean emptyTitle;
	private boolean onAssignmentButNotPosition;

	public TitleListForm(Title title, boolean emptyTitle) {
		this.id = title.getUuid();
		this.name = title.getName();
		this.emptyTitle = emptyTitle;
	}

	public TitleListForm(Title title, boolean emptyTitle, boolean onAssignmentButNotPosition) {
		this.id = title.getUuid();
		this.name = title.getName();
		this.emptyTitle = emptyTitle;
		this.onAssignmentButNotPosition = onAssignmentButNotPosition;
	}
}

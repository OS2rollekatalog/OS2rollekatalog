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
	
	public TitleListForm(Title title) {
		this.id = title.getUuid();
		this.name = title.getName();
	}
}

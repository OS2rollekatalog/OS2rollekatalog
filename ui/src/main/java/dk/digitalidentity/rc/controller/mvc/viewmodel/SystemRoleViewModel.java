package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.SystemRole;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SystemRoleViewModel {
	private long id;
	private String name;
	private String description;
	private String identifier;
	private boolean inUse;

	public SystemRoleViewModel(SystemRole sr, boolean inUse) {
		this.id = sr.getId();
		this.name = sr.getName();
		this.description = sr.getDescription();
		this.identifier = sr.getIdentifier();
		this.inUse = inUse;
	}
}

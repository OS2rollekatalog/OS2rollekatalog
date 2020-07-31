package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ItSystemForm {
	private long id;

	private String name;

	private String identifier;

	private String email;

	private Integer expandToBsr;

	private ItSystemType systemType;
}

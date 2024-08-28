package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;

@Getter
@Setter
public class ItSystemForm {
	private long id;

	@Length(max = 255)
	private String name;
	@Length(max = 64)
	private String identifier;

	private String email;

	private Integer expandToBsr;

	private ItSystemType systemType;

	private String domain;

	private String selectedResponsibleUuid;
}

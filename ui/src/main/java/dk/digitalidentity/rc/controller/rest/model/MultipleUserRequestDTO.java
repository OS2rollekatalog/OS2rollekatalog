package dk.digitalidentity.rc.controller.rest.model;

import java.util.List;

import dk.digitalidentity.rc.dao.model.enums.RequestAction;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MultipleUserRequestDTO {
	private String reason;
	private List<String> selectedUsers;
	private Long roleId;
	private String roleType;
	private String orgUnitUuid;
	private RequestAction action;
	private List<PostponedConstraintDTO> constraints;
}

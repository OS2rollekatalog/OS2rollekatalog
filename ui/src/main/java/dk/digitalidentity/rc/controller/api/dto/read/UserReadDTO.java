package dk.digitalidentity.rc.controller.api.dto.read;

import java.util.ArrayList;
import java.util.List;

import dk.digitalidentity.rc.service.model.AssignedThrough;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserReadDTO {
	private String uuid;
	private String extUuid;
	private String userId;
	private String name;
	private List<AssignedThrough> assignedThrough = new ArrayList<>();
}

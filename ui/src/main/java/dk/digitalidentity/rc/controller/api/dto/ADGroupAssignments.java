package dk.digitalidentity.rc.controller.api.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ADGroupAssignments {
	private String groupName;
	private List<String> sAMAccountNames;
}

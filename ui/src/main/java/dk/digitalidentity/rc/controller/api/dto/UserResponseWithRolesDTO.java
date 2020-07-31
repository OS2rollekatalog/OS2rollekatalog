package dk.digitalidentity.rc.controller.api.dto;

import java.util.List;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class UserResponseWithRolesDTO extends UserResponseDTO {
	private List<String> userRoles;
	private List<String> systemRoles;
	private List<String> dataRoles;
	private List<String> functionRoles;
	private Map<String, String> roleMap;
}

package dk.digitalidentity.rc.controller.api.dto;

import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@EqualsAndHashCode(callSuper = true)
@Setter
@Getter
public class UserResponseWithOIOBPPDTO extends UserResponseDTO {
	private String oioBPP;
	private Map<String, String> roleMap;
}

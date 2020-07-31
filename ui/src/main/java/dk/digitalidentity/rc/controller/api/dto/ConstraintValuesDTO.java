package dk.digitalidentity.rc.controller.api.dto;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConstraintValuesDTO {
	private String entityId;
	private String name;
	private String type;
	private Map<String, String> valueSet;
}

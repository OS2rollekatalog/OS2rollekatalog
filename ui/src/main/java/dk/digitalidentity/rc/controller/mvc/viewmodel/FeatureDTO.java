package dk.digitalidentity.rc.controller.mvc.viewmodel;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FeatureDTO {
	private String name;
	private String description;
	private boolean enabled;
}

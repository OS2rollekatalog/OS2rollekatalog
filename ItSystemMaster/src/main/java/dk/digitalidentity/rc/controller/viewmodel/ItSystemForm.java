package dk.digitalidentity.rc.controller.viewmodel;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ItSystemForm {
	
	@Size(min = 3, max = 64)
	private String name;
}

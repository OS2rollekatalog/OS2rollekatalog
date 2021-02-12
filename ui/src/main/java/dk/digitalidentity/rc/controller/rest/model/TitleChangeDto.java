package dk.digitalidentity.rc.controller.rest.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TitleChangeDto {
	private String[] titleUuidsToAdd;
	private String[] titleUuidsToRemove;
}

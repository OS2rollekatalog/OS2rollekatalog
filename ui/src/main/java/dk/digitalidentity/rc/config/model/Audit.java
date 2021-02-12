package dk.digitalidentity.rc.config.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Audit {
	private int uiDays = 7;
	private int monthRetention = 6;
}

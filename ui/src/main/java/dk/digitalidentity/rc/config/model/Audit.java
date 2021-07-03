package dk.digitalidentity.rc.config.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Audit {
	// TODO: remove - not used any more (the days part)
	private int uiDays = 7;
	private int monthRetention = 6;
}

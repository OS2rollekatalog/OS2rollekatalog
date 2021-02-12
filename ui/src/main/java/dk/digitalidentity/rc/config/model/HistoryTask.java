package dk.digitalidentity.rc.config.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HistoryTask {
	private long retention = 180;
	private boolean enabled = true;
}

package dk.digitalidentity.rc.config.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Scheduled {
	private boolean enabled = false;
	private HistoryTask history = new HistoryTask();
}

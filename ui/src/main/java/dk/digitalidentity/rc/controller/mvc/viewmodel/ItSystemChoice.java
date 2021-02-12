package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ItSystemChoice {
	private long itSystemId;
	private String itSystemName;
	
	public ItSystemChoice(HistoryItSystem it) {
		this.itSystemId = it.getItSystemId();
		this.itSystemName = it.getItSystemName();
	}
}

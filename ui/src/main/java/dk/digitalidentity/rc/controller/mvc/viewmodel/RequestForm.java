package dk.digitalidentity.rc.controller.mvc.viewmodel;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestForm {
	private long id;
	private String reason;
	
	public RequestForm() {
		
	}
	
	public RequestForm(long id) {
		this.id = id;
	}
}

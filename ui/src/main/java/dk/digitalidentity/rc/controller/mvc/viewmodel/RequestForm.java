package dk.digitalidentity.rc.controller.mvc.viewmodel;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RequestForm {
	private long roleId;
	private String userUuid;
	private String reason;

	public RequestForm(String userUuid) {
		this.userUuid = userUuid;
	}
}

package dk.digitalidentity.rc.controller.mvc.viewmodel;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RequestUserDTO {
	private boolean checked;
	private boolean locked;
	private String name;
	private String userId;
	private String uuid;
	private String title;
}

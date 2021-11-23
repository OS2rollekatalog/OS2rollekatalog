package dk.digitalidentity.rc.controller.mvc.viewmodel;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KleViewModel {
	private long id;
	private String code;
	private String name;
	private boolean active;
	private String parent;
}

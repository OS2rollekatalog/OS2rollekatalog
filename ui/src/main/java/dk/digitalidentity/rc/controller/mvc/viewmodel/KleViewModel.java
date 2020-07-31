package dk.digitalidentity.rc.controller.mvc.viewmodel;

import lombok.Data;

@Data
public class KleViewModel {
	private long id;
	private String code;
	private String name;
	private boolean active;
	private String parent;
}

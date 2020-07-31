package dk.digitalidentity.rc.controller.mvc.viewmodel;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KleDTO {
	private String id;
	private String parent;
	private String text;
	private KleDTOState state;
	
	public KleDTO() {
		this.state = new KleDTOState();
	}
	
	public KleDTO(KleDTO other) {
		this.id = other.id;
		this.parent = other.parent;
		this.text = other.text;
		this.state = new KleDTOState();
	}
}

package dk.digitalidentity.rc.controller.api.dto;

import dk.digitalidentity.rc.dao.model.Title;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TitleDTO {
	private String uuid;
	private String name;

	public TitleDTO(Title t) {
		this.uuid = t.getUuid();
		this.name = t.getName();
	}
}

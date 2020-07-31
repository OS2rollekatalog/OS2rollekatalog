package dk.digitalidentity.rc.service.kombit.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KOMBITSystemRoleDTO {
	private String navn;
	private String uuid;
	private String entityId;
	private String beskrivelse;
	private List<KOMBITConstraintsDTO> dataafgraensningstyper;
}

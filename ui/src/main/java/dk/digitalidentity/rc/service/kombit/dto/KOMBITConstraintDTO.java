package dk.digitalidentity.rc.service.kombit.dto;

import java.util.List;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KOMBITConstraintDTO {
	private String uuid;
	private String entityId;
	private String navn;
	private String beskrivelse;
	private String type;
	private String regulaertUdtryk;
	private List<KOMBITVaerdiListeDTO> vaerdiListe;
	
	// the "is" prefix gives Lombok issues
	@Getter(value = AccessLevel.NONE)
	@Setter(value = AccessLevel.NONE)
	private boolean isCommon;
	
	public void setIsCommon(boolean value) {
		this.isCommon = value;
	}

	public boolean getIsCommon() {
		return this.isCommon;
	}
}

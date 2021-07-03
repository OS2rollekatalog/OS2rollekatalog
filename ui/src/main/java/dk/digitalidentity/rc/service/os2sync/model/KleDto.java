package dk.digitalidentity.rc.service.os2sync.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KleDto {

	@JsonProperty("UUID")
	private String uuid;
	
	@JsonProperty("BrugervendtNoegle")
	private String code;
	
	@JsonProperty("Titel")
	private String title;
	
	@JsonProperty("Tilstand")
	private boolean active;
}

package dk.digitalidentity.rc.service.os2sync.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KleDtoWrapper {

	@JsonProperty("value")
	private KleDto[] content;
}

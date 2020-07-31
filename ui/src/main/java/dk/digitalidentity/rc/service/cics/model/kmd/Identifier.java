package dk.digitalidentity.rc.service.cics.model.kmd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Identifier {
	private String type;

	@JsonProperty(value = "id")
	private String id;
}

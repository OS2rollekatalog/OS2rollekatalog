package dk.digitalidentity.rc.service.cics.model.kmd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchEntry {
	private Identifier identifier;
	private Attributes attributes;
}

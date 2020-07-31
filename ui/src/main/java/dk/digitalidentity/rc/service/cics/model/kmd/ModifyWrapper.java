package dk.digitalidentity.rc.service.cics.model.kmd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModifyWrapper {
	private String result;
}

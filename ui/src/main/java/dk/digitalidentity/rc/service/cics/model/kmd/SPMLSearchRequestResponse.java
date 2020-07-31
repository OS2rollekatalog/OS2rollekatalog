package dk.digitalidentity.rc.service.cics.model.kmd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class SPMLSearchRequestResponse {
	private String spmlSearchRequestResult;
}

package dk.digitalidentity.rc.controller.mvc.viewmodel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AttestationConfirmADListDTO {
	private String userUuid;

	@JsonCreator
	public AttestationConfirmADListDTO(@JsonProperty("userUuid") String userUuid) {
		this.userUuid = userUuid;
	}
}

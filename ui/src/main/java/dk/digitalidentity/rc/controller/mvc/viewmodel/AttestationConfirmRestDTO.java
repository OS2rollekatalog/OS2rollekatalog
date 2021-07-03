package dk.digitalidentity.rc.controller.mvc.viewmodel;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AttestationConfirmRestDTO {
	private List<AttestationConfirmUnitListDTO> aprovedUnit;
	private List<AttestationConfirmPersonalListDTO> aprovedPersonal;
	private List<AttestationConfirmPersonalListDTO> toBeRemoved;
	private List<AttestationConfirmUnitListDTO> toEmail;
	private List<AttestationConfirmShowDTO> dtoShowToEmail;
	private List<AttestationConfirmShowDTO> dtoShowToBeRemoved;
	private List<AttestationConfirmShowDTO> dtoShowAprovedPersonal;
	private List<AttestationConfirmShowDTO> dtoShowAprovedUnit;
	private String orgUnitUuid;
	private String message;
}

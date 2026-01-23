package dk.digitalidentity.rc.controller.api.model;

import dk.digitalidentity.rc.dao.model.UserOUFunction;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserOUFunctionDTO {
	private String ouUuid;
	private String functionUuid;

	public UserOUFunctionDTO(UserOUFunction userOUFunction) {
		this.ouUuid = userOUFunction.getOrgUnit() != null ? userOUFunction.getOrgUnit().getUuid() : null;
		this.functionUuid = userOUFunction.getFunction() != null ? userOUFunction.getFunction().getUuid() : null;
	}
}

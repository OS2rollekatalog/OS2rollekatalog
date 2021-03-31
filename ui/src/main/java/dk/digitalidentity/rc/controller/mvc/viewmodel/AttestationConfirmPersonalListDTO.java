package dk.digitalidentity.rc.controller.mvc.viewmodel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import dk.digitalidentity.rc.service.model.AssignedThrough;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AttestationConfirmPersonalListDTO {
	private String userUuid;
	private String roleType;
	private String itSystemName;
	private long roleId;
	private AssignedThrough assignedThrough;
	
    @JsonCreator
    public AttestationConfirmPersonalListDTO(@JsonProperty("userUuid") String userUuid, @JsonProperty("roleType") String roleType, @JsonProperty("itSystemName") String itSystemName, @JsonProperty("roleId") long roleId, @JsonProperty("assignedThrough") AssignedThrough assignedThrough) {
	    this.userUuid = userUuid;
	    this.roleType = roleType;
	    this.roleId = roleId;
	    this.itSystemName = itSystemName;
	    this.assignedThrough = assignedThrough;
  }
}

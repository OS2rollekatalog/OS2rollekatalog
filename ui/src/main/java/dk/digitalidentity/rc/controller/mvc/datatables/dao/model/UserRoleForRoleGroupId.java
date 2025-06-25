package dk.digitalidentity.rc.controller.mvc.datatables.dao.model;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
public class UserRoleForRoleGroupId implements Serializable {
	private Long id;           // userrole id
	private Long rolegroupId;  // rolegroup id
}


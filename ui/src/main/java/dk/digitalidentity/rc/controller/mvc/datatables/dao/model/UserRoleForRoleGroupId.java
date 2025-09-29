package dk.digitalidentity.rc.controller.mvc.datatables.dao.model;

import java.io.Serializable;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class UserRoleForRoleGroupId implements Serializable {
    private static final long serialVersionUID = -5244477552739641900L;
    private Long id; // userrole id
    private Long rolegroupId; // rolegroup id
}

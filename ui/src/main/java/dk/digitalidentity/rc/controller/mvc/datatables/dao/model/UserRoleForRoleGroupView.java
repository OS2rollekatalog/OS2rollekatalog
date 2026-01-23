package dk.digitalidentity.rc.controller.mvc.datatables.dao.model;

import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "user_roles") // Point to base table, not view
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleForRoleGroupView {
	@EmbeddedId
	private UserRoleForRoleGroupId compositeKey;
	@Column
	private String name;
	@Column
	private String description;
	@Column
	private String itSystemName;
	@Column(name = "it_system_id")
	private long itSystemId;
	@Column
	private Boolean selected;
	@Column
	private boolean readOnly;
	@Column
	private String delegatedFromCvr;
	@Column
	@Enumerated(EnumType.STRING)
	private ItSystemType itSystemType;
}

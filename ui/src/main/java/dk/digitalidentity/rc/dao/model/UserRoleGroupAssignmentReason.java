package dk.digitalidentity.rc.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "user_role_group_reason")
@Getter
@Setter
@NoArgsConstructor
public class UserRoleGroupAssignmentReason {

	@Id
	private long id;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_role_group_id")
	private UserRoleGroupAssignment assignment;

	@Column(nullable = false)
	private String reason;

	public UserRoleGroupAssignmentReason(UserRoleGroupAssignment assignment, String reason) {
		this.assignment = assignment;
		this.reason = reason;
	}
}

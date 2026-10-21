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

@Entity(name = "user_user_role_reason")
@Getter
@Setter
@NoArgsConstructor
public class UserUserRoleAssignmentReason {

	@Id
	private long id;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_user_role_id")
	private UserUserRoleAssignment assignment;

	@Column(nullable = false)
	private String reason;

	public UserUserRoleAssignmentReason(UserUserRoleAssignment assignment, String reason) {
		this.assignment = assignment;
		this.reason = reason;
	}
}

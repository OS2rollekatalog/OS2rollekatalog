package dk.digitalidentity.rc.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

/**
 * a mapping class used to keep track if a manual IT-system mail is sent
 * a row is created when the "assigned"-mail is sent
 * the row is removed when the "removed"-mail is sent
 */
@Getter
@Setter
@Entity
public class ManualAssignmentNotificationMap {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column(nullable = false)
	private long userRoleId;

	@Column(nullable = false)
	private String userUserId;

	@Column(nullable = false)
	private long domainId;

	@Column
	private String orgUnitName;

	@Column
	private String assignedBy;
}

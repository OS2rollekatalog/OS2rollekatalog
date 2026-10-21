package dk.digitalidentity.rc.rolerequest.model.entity;

import dk.digitalidentity.rc.config.ApproverPermissionConverter;
import dk.digitalidentity.rc.config.RequesterPermissionConverter;
import dk.digitalidentity.rc.rolerequest.model.permission.ApproverPermission;
import dk.digitalidentity.rc.rolerequest.model.permission.RequesterPermission;
import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * The resolved requester and approver permissions of every user role, including roles in deleted IT systems.
 */
@Getter
@Entity
@Immutable
@Table(name = "view_user_role_permissions")
public class UserRolePermissionView {

	@Id
	@Column(name = "user_role_id")
	private long id;

	@Column
	private long itSystemId;

	@Column
	private boolean readOnly;

	@Column
	@Convert(converter = RequesterPermissionConverter.class)
	private RequesterPermission effectiveRequesterPermission;

	@Column
	@Convert(converter = ApproverPermissionConverter.class)
	private ApproverPermission effectiveApproverPermission;
}

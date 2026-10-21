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
 * The resolved requester and approver permissions of every role group.
 */
@Getter
@Entity
@Immutable
@Table(name = "view_role_group_permissions")
public class RoleGroupPermissionView {

	@Id
	@Column(name = "role_group_id")
	private long id;

	@Column
	@Convert(converter = RequesterPermissionConverter.class)
	private RequesterPermission effectiveRequesterPermission;

	@Column
	@Convert(converter = ApproverPermissionConverter.class)
	private ApproverPermission effectiveApproverPermission;
}

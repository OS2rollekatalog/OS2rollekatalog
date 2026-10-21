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
 * The resolved requester and approver permissions of every IT system.
 */
@Getter
@Entity
@Immutable
@Table(name = "view_it_system_permissions")
public class ItSystemPermissionView {

	@Id
	@Column(name = "it_system_id")
	private long id;

	@Column
	@Convert(converter = RequesterPermissionConverter.class)
	private RequesterPermission effectiveRequesterPermission;

	@Column
	@Convert(converter = ApproverPermissionConverter.class)
	private ApproverPermission effectiveApproverPermission;
}

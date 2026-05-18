package dk.digitalidentity.rc.controller.mvc.datatables.dao.model;

import dk.digitalidentity.rc.config.ApprovableBySetConverter;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;

import com.fasterxml.jackson.annotation.JsonView;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@IdClass(CombinedRoleViewId.class)
@Table(name = "view_datatables_combined_roles")
public class CombinedRoleView {

	@Id
	@Column
	@JsonView(DataTablesOutput.View.class)
	private long id;

	@Id
	@Column
	@JsonView(DataTablesOutput.View.class)
	private String type; // 'userRole' or 'roleGroup'

	@Column
	@JsonView(DataTablesOutput.View.class)
	private String name;

	@Column
	@JsonView(DataTablesOutput.View.class)
	private String description;

	@Column(name = "it_system_id")
	@JsonView(DataTablesOutput.View.class)
	private Long itSystemId;

	@Column(name = "it_system_name")
	@JsonView(DataTablesOutput.View.class)
	private String itSystemName;

	@Column(name = "it_system_type")
	@JsonView(DataTablesOutput.View.class)
	private String itSystemType;

	@Column(name = "it_system_identifier")
	@JsonView(DataTablesOutput.View.class)
	private String itSystemIdentifier;

	@Column(name = "requester_permission")
	@JsonView(DataTablesOutput.View.class)
	private String requesterPermission;

	@Column(name = "approver_permission")
	@JsonView(DataTablesOutput.View.class)
	private String approverPermission;

	@Column(name = "effective_requester_permission")
	@JsonView(DataTablesOutput.View.class)
	private String effectiveRequesterPermission;

	@Column(name = "effective_approver_permission")
	@Convert(converter = ApprovableBySetConverter.class)
	@JsonView(DataTablesOutput.View.class)
	private Set<ApprovableBy> effectiveApproverPermission = new HashSet<>();

	@Column(name = "pending_sync")
	@JsonView(DataTablesOutput.View.class)
	private boolean pendingSync;

	@Column(name = "sync_failed")
	@JsonView(DataTablesOutput.View.class)
	private boolean syncFailed;

	@Column(name = "delegated_from_cvr")
	@JsonView(DataTablesOutput.View.class)
	private String delegatedFromCvr;

	@Column(name = "read_only")
	@JsonView(DataTablesOutput.View.class)
	private boolean readOnly;

	@Column(name = "user_only")
	@JsonView(DataTablesOutput.View.class)
	private boolean userOnly;

	@Column(name = "org_unit_filter_uuids")
	@JsonView(DataTablesOutput.View.class)
	private String orgUnitFilterUuids;

	@Column(name = "it_system_org_unit_filter_uuids")
	@JsonView(DataTablesOutput.View.class)
	private String itSystemOrgUnitFilterUuids;

	@Column(name = "role_within_role_group")
	@JsonView(DataTablesOutput.View.class)
	private String roleWithinRoleGroup;
}

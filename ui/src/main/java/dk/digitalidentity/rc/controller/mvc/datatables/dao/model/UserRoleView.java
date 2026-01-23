package dk.digitalidentity.rc.controller.mvc.datatables.dao.model;

import dk.digitalidentity.rc.config.ApprovableBySetConverter;
import dk.digitalidentity.rc.config.RequestableBySetConverter;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "view_datatables_userroles")
public class UserRoleView {

	@Id
	@Column
	private long id;

	@Column
	private String name;

	@Column
	private String description;

	@Column
	private long itSystemId;

	@Column
	private String itSystemName;

	@Column
	private String itSystemIdentifier;

	@Enumerated(EnumType.STRING)
	@Column
	private ItSystemType itSystemType;

	@Column
	private String requesterPermission;

	@Column
	private String approverPermission;

	@Column
	private boolean pendingSync;

	@Column
	private boolean syncFailed;

	@Column
	private String delegatedFromCvr;

	@Column
	private boolean readOnly;

	@Column
	private String orgUnitFilterUuids;

	@Column
	private String itSystemOrgUnitFilterUuids;

	@Column
	@Convert(converter = RequestableBySetConverter.class)
	private Set<RequestableBy> effectiveRequesterPermission = new HashSet<>();

	@Column
	@Convert(converter = ApprovableBySetConverter.class)
	private Set<ApprovableBy> effectiveApproverPermission = new HashSet<>();

}

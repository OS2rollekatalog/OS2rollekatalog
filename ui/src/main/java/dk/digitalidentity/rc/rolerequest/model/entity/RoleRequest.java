package dk.digitalidentity.rc.rolerequest.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dk.digitalidentity.rc.dao.model.*;
import dk.digitalidentity.rc.dao.model.enums.RequestAction;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;
import dk.digitalidentity.rc.log.AuditLoggable;
import dk.digitalidentity.rc.rolerequest.model.enums.ApproverOption;
import jakarta.persistence.*;
import lombok.*;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "req_role_request")
public class RoleRequest implements AuditLoggable {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "requester_uuid")
	private User requester;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reciever_uuid")
	private User receiver;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ou_uuid")
	private OrgUnit orgUnit;


	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rolegroup_id")
	private RoleGroup roleGroup;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_role_id")
	private UserRole userRole;

	@Column
	private String reason;

	@Column
	private String rejectReason;


	@Column
	private boolean roleAssignerNotified;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RequestApproveStatus status;

	@Column
	private Date requestTimestamp;

	@Column
	private Date statusTimestamp;

	@Column
	private boolean emailSent;

    @Column
    private String requestGroupIdentifier;

	@Column
	@Enumerated(EnumType.STRING)
	private RequestAction requestAction;

	@Column
	@Enumerated(EnumType.STRING)
	private ApproverOption approverOption;

    @OneToMany(mappedBy = "roleRequest", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RequestPostponedConstraint> requestPostponedConstraints;

	@JsonIgnore
	@Override
	public String getEntityId() {
		return Long.toString(id);
	}

	@Override
	public String getEntityName() {
		return "Request";
	}
}

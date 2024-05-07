package dk.digitalidentity.rc.dao.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dk.digitalidentity.rc.dao.model.enums.EntityType;
import dk.digitalidentity.rc.dao.model.enums.RequestAction;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;
import dk.digitalidentity.rc.log.AuditLoggable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Entity
@Table(name = "request_approve")
@Getter
@Setter
public class RequestApprove implements AuditLoggable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "requester_uuid")
	private User requester;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assigner_uuid")
	private User assigner;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "requested_for_uuid")
	private User requestedFor;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ou_uuid")
	private OrgUnit orgUnit;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EntityType roleType;
	
	@Enumerated(EnumType.STRING)
	@Column
	private RequestAction requestAction;
	
	@Column
	private Long roleId;
	
	@Column
	private String reason;
	
	@Column
	private String rejectReason;
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RequestApproveStatus status;
	
	@Column
	private boolean roleAssignerNotified;
	
	@Column
	private Date requestTimestamp;

	@Column
	private Date statusTimestamp;
	
	@Column
	private boolean emailSent;
	
	@Column
	private String adminUuid;
	
	@Column
	private String adminName;

	@OneToMany(mappedBy = "requestApprove", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<RequestApprovePostponedConstraint> requestApprovePostponedConstraints;

	@JsonIgnore
	@Override
	public String getEntityId() {
		return Long.toString(id);
	}

	@Override
	public String getEntityName() {
		return "Request"; // TODO: anything better to put in this column?
	}
}

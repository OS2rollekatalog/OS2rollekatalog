package dk.digitalidentity.rc.rolerequest.model.entity;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.rc.config.ApprovableByListConverter;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.RequestAction;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;
import dk.digitalidentity.rc.log.AuditLoggable;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
	private LocalDate startDate;

	@Column
	private LocalDate endDate;

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
	private boolean emailSentToServicedesk;

    @Column
    private String requestGroupIdentifier;

	@Column
	@Enumerated(EnumType.STRING)
	private RequestAction requestAction;

	@Column
	@Convert(converter = ApprovableByListConverter.class)
	private List<ApprovableBy> approverOption;

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

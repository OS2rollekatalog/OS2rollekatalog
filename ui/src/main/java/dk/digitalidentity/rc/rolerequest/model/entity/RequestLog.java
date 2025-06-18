package dk.digitalidentity.rc.rolerequest.model.entity;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.rolerequest.log.RequestLogEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Date;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "req_request_log")
public class RequestLog {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column
	private LocalDateTime requestTimestamp;

	@NotNull
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "acting_user_uuid")
	private User actingUser;

	@NotNull
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "target_user_uuid")
	private User targetUser;

	@NotNull
	@Column
	@Enumerated(EnumType.STRING)
	private RequestLogEvent requestEvent;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_role_id")
	private UserRole userRole;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rolegroup_id")
	private RoleGroup roleGroup;

	@Column
	private String details;
}

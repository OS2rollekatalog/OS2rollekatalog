package dk.digitalidentity.rc.rolerequest.model.entity;

import java.time.LocalDateTime;

import dk.digitalidentity.rc.rolerequest.log.RequestLogEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
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
@Table(name = "req_request_log")
public class RequestLog {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column
	private LocalDateTime requestTimestamp;

	@Column
	private String actingUserUuid;

	@Column
	private String actingUsername;

	@Column
	private String targetUserUuid;

	@Column
	private String targetUsername;

	@NotNull
	@Column
	@Enumerated(EnumType.STRING)
	private RequestLogEvent requestEvent;

	@Column
	private Long userRoleId;

	@Column
	private String roleName;

	@Column
	private Long rolegroupId;

	@Column
	private String rolegroupName;

	@Column
	private String details;
}

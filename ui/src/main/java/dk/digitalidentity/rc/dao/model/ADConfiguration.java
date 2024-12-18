package dk.digitalidentity.rc.dao.model;

import dk.digitalidentity.rc.dao.model.json.ADConfigurationJSON;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;

import java.time.LocalDateTime;

@Table(name = "ad_configuration",
		uniqueConstraints = { @UniqueConstraint(name = "UQ_VERSION_CLIENT", columnNames = { "version", "client_id" }) })
@Getter
@Setter
@Entity(name = "ad_configuration")
public class ADConfiguration {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column(nullable = false)
	private int version;

	@CreationTimestamp
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	@Column(nullable = false)
	private String updatedBy;

	@Column
	private String errorMessage;

	@Column(columnDefinition = "json")
	@JdbcTypeCode(SqlTypes.JSON)
	private ADConfigurationJSON json;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "client_id")
	private Client client;
}

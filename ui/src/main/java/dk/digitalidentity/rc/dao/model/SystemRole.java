package dk.digitalidentity.rc.dao.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dk.digitalidentity.rc.dao.model.enums.RoleType;
import dk.digitalidentity.rc.log.AuditLoggable;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "system_roles")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter
@Setter
public class SystemRole implements AuditLoggable {

	@Id
	@JsonIgnore
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	private String uuid;

	@JsonIgnore
	@Column(nullable = false, length = 64)
	private String name;

	@Column(nullable = false, length = 128)
	private String identifier;

	@JsonIgnore
	@Column(nullable = true)
	private String description;
	
	@Column(nullable = false)
	private int weight = 1;

	@JsonIgnore
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "it_system_id")
	private ItSystem itSystem;
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RoleType roleType = RoleType.BOTH;
	
	@JsonIgnore
	@ElementCollection(fetch = FetchType.LAZY, targetClass = ConstraintTypeSupport.class)
	@CollectionTable(name = "system_role_supported_constraints", joinColumns = @JoinColumn(name = "system_role_id"))
	private List<ConstraintTypeSupport> supportedConstraintTypes;

	@JsonIgnore
	@Override
	public String getEntityId() {
		return Long.toString(id);
	}

	@Override
	public String getEntityName() {
		return name;
	}
}

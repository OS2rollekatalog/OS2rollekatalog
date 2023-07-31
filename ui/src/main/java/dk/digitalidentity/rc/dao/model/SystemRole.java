package dk.digitalidentity.rc.dao.model;

import java.util.List;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import dk.digitalidentity.rc.dao.model.enums.RoleType;
import dk.digitalidentity.rc.log.AuditLoggable;
import lombok.Getter;
import lombok.Setter;

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

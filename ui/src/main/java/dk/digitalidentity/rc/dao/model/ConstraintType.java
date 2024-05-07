package dk.digitalidentity.rc.dao.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dk.digitalidentity.rc.dao.model.enums.ConstraintUIType;
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
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity(name = "constraint_types")
public class ConstraintType {

	@Id
	@JsonIgnore
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@Column
	private String uuid; // KOMBIT ready unique ID
	
	@Column
	private String entityId; // KOMBIT ready unique ID
	
	@Column
	private String name;
	
	@Column
	private String description;

	@Column
	@Enumerated(EnumType.STRING)
	private ConstraintUIType uiType;

	@JsonIgnore
	@ElementCollection(fetch = FetchType.LAZY, targetClass = ConstraintTypeValueSet.class)
	@CollectionTable(name = "constraint_type_value_sets", joinColumns = @JoinColumn(name = "constraint_type_id"))
	private List<ConstraintTypeValueSet> valueSet;
	
	@Column
	private String regex;
}

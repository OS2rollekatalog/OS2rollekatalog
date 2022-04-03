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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import dk.digitalidentity.rc.dao.model.enums.ConstraintUIType;
import lombok.Getter;
import lombok.Setter;

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

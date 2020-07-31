package dk.digitalidentity.rc.dao.model;

import javax.persistence.Column;
import javax.persistence.Embeddable;

import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class ConstraintTypeValueSet {

	@Column
	private String constraintKey;
	
	@Column
	private String constraintValue;
}

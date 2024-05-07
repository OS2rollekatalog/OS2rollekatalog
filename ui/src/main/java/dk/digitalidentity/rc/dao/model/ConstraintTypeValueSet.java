package dk.digitalidentity.rc.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
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

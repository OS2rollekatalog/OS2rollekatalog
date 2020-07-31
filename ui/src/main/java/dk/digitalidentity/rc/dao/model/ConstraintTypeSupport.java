package dk.digitalidentity.rc.dao.model;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class ConstraintTypeSupport {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "constraint_type_id")
	private ConstraintType constraintType;

	@Column
	private boolean mandatory;
}

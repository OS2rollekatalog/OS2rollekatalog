package dk.digitalidentity.rc.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

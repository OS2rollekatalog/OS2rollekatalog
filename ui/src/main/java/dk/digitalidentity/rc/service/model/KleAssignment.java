package dk.digitalidentity.rc.service.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
@EqualsAndHashCode(of = { "code" })
public class KleAssignment {
	private String code;
	private String description;
	private String inheritedFrom;
}

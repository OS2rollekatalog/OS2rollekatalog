package dk.digitalidentity.rc.rolerequest.model.dto;

import jakarta.persistence.Column;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestConstraintDTO {
	private Long id;
	private String value;
}

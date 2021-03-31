package dk.digitalidentity.rc.service.model;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RoleWithDateDTO {
	private long id;
	private LocalDate startDate;
	private LocalDate stopDate;
}

package dk.digitalidentity.rc.controller.mvc.viewmodel;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserRoleCheckedDTO {
	private String uuid;
	private String name;
	private String userId;
	private boolean checked;
	private LocalDate startDate;
	private LocalDate stopDate;
}

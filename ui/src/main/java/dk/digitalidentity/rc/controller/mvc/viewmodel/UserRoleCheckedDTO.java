package dk.digitalidentity.rc.controller.mvc.viewmodel;

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

}

package dk.digitalidentity.rc.controller.api.model;

import dk.digitalidentity.rc.dao.model.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ManagerExtendedDTO {
	private String uuid;
	private String name;
	private String userId;
	private String managerSubstitute;

    public ManagerExtendedDTO(User user) {
        this.uuid = user.getUuid();
        this.name = user.getName();
        this.userId = user.getUserId();
        this.managerSubstitute = user.getManagerSubstitute() != null ? user.getManagerSubstitute().getUserId() : null;
    }
}

package dk.digitalidentity.rc.controller.api.model;

import dk.digitalidentity.rc.dao.model.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ManagerDTO {
	private String uuid;
	private String userId;

    public ManagerDTO(User manager) {
        this.uuid = manager.getUuid();
        this.userId = manager.getUserId();
    }
}

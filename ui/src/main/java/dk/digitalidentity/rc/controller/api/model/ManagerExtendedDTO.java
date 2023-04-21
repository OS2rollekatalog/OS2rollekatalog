package dk.digitalidentity.rc.controller.api.model;

import java.util.List;

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
	private List<ManagerSubstituteDTO> managerSubstitutes;

    public ManagerExtendedDTO(User user) {
        this.uuid = user.getUuid();
        this.name = user.getName();
        this.userId = user.getUserId();
        this.managerSubstitutes = user.getManagerSubstitutes().stream().map(ManagerSubstituteDTO::new).toList();
    }
}

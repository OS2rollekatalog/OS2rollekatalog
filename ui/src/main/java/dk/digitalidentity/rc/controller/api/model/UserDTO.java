package dk.digitalidentity.rc.controller.api.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserKLEMapping;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserDTO {
	private String extUuid;
	private String userId;
	private String name;
	private String email;
	private String phone;
	private String cpr;
	private boolean doNotInherit;
	private List<PositionDTO> positions;
	private List<String> klePerforming;
	private List<String> kleInterest;

	public UserDTO(User user) {
		this.extUuid = user.getExtUuid();
		this.userId = user.getUserId();
		this.name = user.getName();
		this.email = user.getEmail();
		this.phone = user.getPhone();
		this.cpr = user.getCpr();
		this.doNotInherit = user.isDoNotInherit();

		List<Position> userPositions = user.getPositions();
		if (userPositions != null) {
			this.positions = userPositions.stream().map(PositionDTO::new).collect(Collectors.toList());
		}

		List<UserKLEMapping> kles = user.getKles();
		if (kles != null) {
			this.kleInterest = new ArrayList<>();
			this.klePerforming = new ArrayList<>();

			kles.stream().forEach(userKLEMapping -> {
				if (KleType.PERFORMING.equals(userKLEMapping.getAssignmentType())) {
					klePerforming.add(userKLEMapping.getCode());
				}
				else if (KleType.INTEREST.equals(userKLEMapping.getAssignmentType())) {
					kleInterest.add(userKLEMapping.getCode());
				}
			});
		}
	}
}

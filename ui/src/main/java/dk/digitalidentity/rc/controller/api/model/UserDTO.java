package dk.digitalidentity.rc.controller.api.model;

import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserKLEMapping;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
	private String nemloginUuid;
	private boolean doNotInherit;
	private boolean disabled;
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
		this.nemloginUuid = user.getNemloginUuid();
		this.doNotInherit = user.getPositions().stream().anyMatch(p -> p.isDoNotInherit());
		this.disabled = user.isDisabled();

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

	public UserDTO(String extUuid, String userId, String name, String email, String phone, String cpr, String nemloginUuid, boolean doNotInherit, boolean disabled, List<PositionDTO> positions, List<String> klePerforming, List<String> kleInterest) {
		this.extUuid = extUuid;
		this.userId = userId;
		this.name = name;
		this.email = email;
		this.phone = phone;
		this.cpr = cpr;
		this.nemloginUuid = nemloginUuid;
		this.doNotInherit = doNotInherit;
		this.disabled = disabled;
		this.positions = positions;
		this.klePerforming = klePerforming;
		this.kleInterest = kleInterest;
	}
}

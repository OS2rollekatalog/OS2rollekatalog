package dk.digitalidentity.rc.service.nemlogin.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TokenResponse {
	private String accessToken;
	private String tokenType;
	private int expiresIn;
}

package dk.digitalidentity.rc.security;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class ExtendedUser extends User {
	private static final long serialVersionUID = 1L;

	public ExtendedUser(String username, String password, List<GrantedAuthority> authorities) {
		super(username, password, authorities);
	}
}

package dk.digitalidentity.rc.security;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExtendedUser extends User {
	private static final long serialVersionUID = 1L;

	public ExtendedUser(String username, String password, List<GrantedAuthority> authorities) {
		super(username, password, authorities);
	}
}

package dk.digitalidentity.rc.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.JdbcUserDetailsManager;

import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.model.User;

public class ExtendedUserDetailsManager extends JdbcUserDetailsManager {
	
	@Autowired
	private UserDao userDao;
	
	@Override
	public UserDetails loadUserByUsername(String username) {
		User user = userDao.getByUsername(username);
		
		if (user == null) {
			throw new UsernameNotFoundException("User with username '" + username + "' was not found in the users table");
		}

		List<GrantedAuthority> authorities = new ArrayList<>();		
		GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_USER");
		authorities.add(authority);

        return new ExtendedUser(user.getUsername(), user.getPassword(), authorities);
	}
}

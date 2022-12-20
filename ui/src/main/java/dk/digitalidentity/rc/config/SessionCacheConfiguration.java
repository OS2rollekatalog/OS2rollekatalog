package dk.digitalidentity.rc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.MySqlJdbcIndexedSessionRepositoryCustomizer;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableJdbcHttpSession
public class SessionCacheConfiguration {

	@Bean
	public CookieSerializer cookieSerializer() {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName("JSESSIONID");
		serializer.setCookiePath("/");
		serializer.setUseSecureCookie(true);
		serializer.setSameSite("None");
		serializer.setCookieMaxAge(8 * 60 * 60); // 8 hours

		return serializer;
	}

	// Spring Session JDBC optimizations for MySQL
    @Bean
    public MySqlJdbcIndexedSessionRepositoryCustomizer sessionRepositoryCustomizer() {
        return new MySqlJdbcIndexedSessionRepositoryCustomizer();
    }
}

package dk.digitalidentity.rc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableJdbcHttpSession(cleanupCron = "#{roleCatalogueConfiguration.scheduled.enabled ? '0 * * * * *' : '0 0 3 29 2 *'}", maxInactiveIntervalInSeconds = 8 * 60 * 60)
public class SessionCacheConfiguration {

	@Bean
	public CookieSerializer cookieSerializer() {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName("JSESSIONID");
		serializer.setCookiePath("/");
		serializer.setUseSecureCookie(true);
		serializer.setSameSite("None");

		return serializer;
	}
}

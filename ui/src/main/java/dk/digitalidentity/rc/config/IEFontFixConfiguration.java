package dk.digitalidentity.rc.config;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.servlet.DispatcherType;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dk.digitalidentity.rc.filter.InternetExplorerHttpsFontFilter;

@Configuration
public class IEFontFixConfiguration {

	@Bean
	public FilterRegistrationBean<InternetExplorerHttpsFontFilter> fixIeHttpsFontCache() {
	    List<String> urlPatterns = new ArrayList<>();
	    urlPatterns.add("/webjars/font-awesome/*");

		FilterRegistrationBean<InternetExplorerHttpsFontFilter> registration = new FilterRegistrationBean<>();
		registration.setFilter(new InternetExplorerHttpsFontFilter());
		registration.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
		registration.setUrlPatterns(urlPatterns);

		return registration;
	}
}

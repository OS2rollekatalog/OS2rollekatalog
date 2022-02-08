package dk.digitalidentity.rc.config;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.servlet.DispatcherType;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dk.digitalidentity.rc.log.RequestLogger;
import dk.digitalidentity.rc.log.SecurityLogger;

@Configuration
public class RequestLoggerConfiguration {

	@Bean
	public RequestLogger logFilter(SecurityLogger logger) {
		RequestLogger filter = new RequestLogger(logger);
		filter.setIncludeQueryString(true);
		filter.setIncludePayload(false);
		filter.setIncludeHeaders(false);
		filter.setBeforeMessagePrefix("");
		filter.setBeforeMessageSuffix("");

		return filter;
	}
	
	@Bean
	public FilterRegistrationBean<RequestLogger> logFilterRegistration(RequestLogger filter) {
		List<String> urlPatterns = new ArrayList<>();
		urlPatterns.add("/api/*");

		FilterRegistrationBean<RequestLogger> registration = new FilterRegistrationBean<>();
		registration.setFilter(filter);
		registration.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
		registration.setUrlPatterns(urlPatterns);
		
		return registration;
	}
}

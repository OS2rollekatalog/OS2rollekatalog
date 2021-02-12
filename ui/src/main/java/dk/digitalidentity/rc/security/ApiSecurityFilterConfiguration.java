package dk.digitalidentity.rc.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;

@Configuration
public class ApiSecurityFilterConfiguration {

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Bean
	public FilterRegistrationBean<ApiSecurityFilter> apiSecurityFilter() {
		ApiSecurityFilter filter = new ApiSecurityFilter();
		filter.setApiKeys(configuration.getCustomer().getApikey());

		FilterRegistrationBean<ApiSecurityFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.addUrlPatterns("/api/*");

		return filterRegistrationBean;
	}
}

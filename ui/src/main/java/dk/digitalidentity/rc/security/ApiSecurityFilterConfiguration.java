package dk.digitalidentity.rc.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiSecurityFilterConfiguration {

	@Value("${customer.apikey}")
	private String[] apiKey;

	@Bean
	public FilterRegistrationBean<ApiSecurityFilter> apiSecurityFilter() {
		ApiSecurityFilter filter = new ApiSecurityFilter();
		filter.setApiKeys(apiKey);

		FilterRegistrationBean<ApiSecurityFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.addUrlPatterns("/api/*");

		return filterRegistrationBean;
	}
}

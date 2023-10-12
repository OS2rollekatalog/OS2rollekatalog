package dk.digitalidentity.rc.security;

import dk.digitalidentity.rc.service.ClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiSecurityFilterConfiguration {

	@Autowired
	private ClientService clientService;

	@Bean
	public FilterRegistrationBean<ApiSecurityFilter> apiSecurityFilter() {
		ApiSecurityFilter filter = new ApiSecurityFilter(clientService);

		FilterRegistrationBean<ApiSecurityFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.addUrlPatterns("/api/*", "/manage/info", "/manage/prometheus");
		filterRegistrationBean.setOrder(100);

		return filterRegistrationBean;
	}
}

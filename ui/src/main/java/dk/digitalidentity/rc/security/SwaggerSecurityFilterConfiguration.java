package dk.digitalidentity.rc.security;

import dk.digitalidentity.rc.filter.SwaggerSecurityFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerSecurityFilterConfiguration {
    @Bean
    public FilterRegistrationBean<SwaggerSecurityFilter> internalApiSecurityFilter() {
        SwaggerSecurityFilter filter = new SwaggerSecurityFilter();

        FilterRegistrationBean<SwaggerSecurityFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
        filterRegistrationBean.addUrlPatterns("/swagger-ui", "/swagger-ui/*");
        return filterRegistrationBean;
    }
}

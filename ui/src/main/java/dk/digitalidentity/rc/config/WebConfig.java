package dk.digitalidentity.rc.config;

import dk.digitalidentity.rc.rolerequest.Interceptor.NavigationInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.filter.UrlHandlerFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Autowired
	private NavigationInterceptor navigationInterceptor;

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/webjars/**")
			.addResourceLocations("classpath:/META-INF/resources/webjars/")
			.setCacheControl(CacheControl.maxAge(1, TimeUnit.DAYS));
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(navigationInterceptor).addPathPatterns("/ui/**");
	}

	@Bean
	public FilterRegistrationBean<UrlHandlerFilter> trailingSlashFilter() {
		FilterRegistrationBean<UrlHandlerFilter> registration = new FilterRegistrationBean<>();

		// Handle trailing slash transparently by wrapping the request
		UrlHandlerFilter filter = UrlHandlerFilter
			.trailingSlashHandler("/**")
			.intercept(request -> {
				log.warn("Trailing slash detected and wrapped: {} {}",
					request.getMethod(),
					request.getRequestURI());
			})
			.wrapRequest()
			.build();

		registration.setFilter(filter);
		registration.setOrder(0); // Execute early in filter chain

		return registration;
	}
}

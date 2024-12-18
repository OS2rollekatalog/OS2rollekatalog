package dk.digitalidentity.rc.config;

import dk.digitalidentity.rc.interceptor.ControllerInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Autowired
	private ControllerInterceptor controllerInterceptor;

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/webjars/**")
				.addResourceLocations("classpath:/META-INF/resources/webjars/")
			    .setCacheControl(CacheControl.maxAge(1, TimeUnit.DAYS));
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(controllerInterceptor).addPathPatterns("/ui/**");
	}

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		// TODO KBP 2024-04-26 We need this for now, since redirect: etc. adds a slash which seems silly when spring does not
		// accept a slash in the end unless its specifically added to the endpoint
		configurer.setUseTrailingSlashMatch(true);
	}
}

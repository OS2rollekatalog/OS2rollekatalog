package dk.digitalidentity.rc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

@SpringBootApplication(scanBasePackages = { "dk.digitalidentity" })
@EnableAutoConfiguration(exclude = {
		JdbcTemplateAutoConfiguration.class
})
@OpenAPIDefinition(info = @Info(title = "OS2rollekatalog API", version = "2.0", description = "API til udlæsning af information fra OS2rollekatalog"))
@SecurityScheme(name = "ApiKey", paramName = "ApiKey", scheme = "basic", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER)
public class Application {
	public static void main(String[] args) {
		SpringApplication.run(Application.class);
	}
}

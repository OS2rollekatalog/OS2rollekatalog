package dk.digitalidentity.rc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;

@SpringBootApplication(scanBasePackages = { "dk.digitalidentity" })
@EnableAutoConfiguration(exclude = {
		JdbcTemplateAutoConfiguration.class
})
public class Application {
	public static void main(String[] args) {
		SpringApplication.run(Application.class);
	}
}

package dk.digitalidentity.rc.config;

import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MapperConfiguration {

	// TODO: can we kill this from the codebase entirely - it is slow, and manual mapping is a lot safer than magical mapping ;)
    @Bean
    public ModelMapper modelMapper(){
        return new ModelMapper();
    }
}

package dk.digitalidentity.rc.controller.mvc;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.rc.config.FeatureDocumentation;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.viewmodel.FeatureDTO;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireAdministratorRole
@Controller
public class FeatureDocumentationController {

	@Autowired
	private RoleCatalogueConfiguration configuration;
	
	@GetMapping(value = "/ui/featuredocumentation")
	public String getFeatureDocumentation(Model model) {
		
		List<FeatureDTO> features = new ArrayList<>();
		getFields(configuration.getClass().getDeclaredFields(), features, configuration);
		
		model.addAttribute("features", features);
	    
		return "feature_documentation/list";
	}

	private void getFields(Field[] fields, List<FeatureDTO> features, Object object) {
	    try {
	        for (Field field : fields) {
	            field.setAccessible(true);

	            if (field.isAnnotationPresent(FeatureDocumentation.class) && field.getType().equals(boolean.class)) {
	            	FeatureDocumentation annotation = field.getAnnotation(FeatureDocumentation.class);

	            	FeatureDTO feature = new FeatureDTO();
	            	feature.setDescription(annotation.description());
	            	feature.setName(annotation.name());
	            	feature.setEnabled(field.getBoolean(object));
	            	features.add(feature);
			    }
	            else {
			    	if (field.getType().getPackageName().startsWith("dk.")) {
			    		getFields(field.getType().getDeclaredFields(), features, field.get(object));
			    	}
			    }
	        }
	    }
	    catch (IllegalArgumentException e) {
	        log.error("A method has been passed a wrong argument in the getFields method for feature documentation.");
	    }
	    catch (SecurityException e) {
	    	log.error("Security violation in the getFields method for feature documentation");
		}
	    catch (IllegalAccessException e) {
			log.error("tries to acces a field or method, that is not allowed from the getFields method for feature documentation");
		}
	}
}

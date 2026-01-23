package dk.digitalidentity.rc.controller.mvc;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.Section;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.rc.config.FeatureDocumentation;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.viewmodel.FeatureDTO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequireControllerPermission(section = Section.CONFIG, permission = Permission.READ)
public class FeatureDocumentationController {

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@GetMapping(value = "/ui/featuredocumentation")
	public String getFeatureDocumentation(Model model) {

		List<FeatureDTO> features = new ArrayList<>();
		getFields(configuration.getClass().getDeclaredFields(), features, configuration, new HashSet<>());

		model.addAttribute("features", features);

		return "feature_documentation/list";
	}

	private void getFields(Field[] fields, List<FeatureDTO> features, Object object, Set<Object> visited) {
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
			    } else {
					// Undgå null, primitive typer og allerede sete objekter
					Object fieldValue = field.get(object);
					if (fieldValue != null && field.getType().getPackageName().startsWith("dk.") && !visited.contains(fieldValue)) {
						visited.add(fieldValue); // markér som besøgt
			    		getFields(field.getType().getDeclaredFields(), features, field.get(object), visited);
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

package dk.digitalidentity.rc.controller.validator;

import java.util.Arrays;

import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.enums.ClientIntegrationType;
import dk.digitalidentity.rc.service.DomainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

import dk.digitalidentity.rc.controller.mvc.viewmodel.ClientDTO;
import dk.digitalidentity.rc.dao.model.Client;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.service.ClientService;

@Component
public class ClientDTOValidator implements Validator {

	@Autowired
	private ClientService clientService;

	@Autowired
	private DomainService domainService;

	@Override
	public boolean supports(Class<?> aClass) {
		return (ClientDTO.class.isAssignableFrom(aClass));
	}

	@Override
	public void validate(Object o, Errors errors) {
		System.out.println("Validator 1");
		ClientDTO clientDTO = (ClientDTO) o;

		if (clientDTO.getId() == 0) {
			Client client = clientService.getClientByName(clientDTO.getName());
			if (client != null) {
				errors.rejectValue("name", "mvc.errors.client.exists");
			}
			
			client = clientService.getClientByApiKeyBypassCache(clientDTO.getApiKey());
			if (client != null) {
				errors.rejectValue("apiKey", "mvc.errors.client.apiKey.invalid");
			}
		}
		System.out.println("Validator 2");

		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "name", "mvc.errors.client.name.required");
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "apiKey", "mvc.errors.client.apiKey.required");
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "accessRole", "mvc.errors.client.accessRole.required");
		System.out.println("Validator 4");

		if (clientDTO.getName().length() < 3) {
			errors.rejectValue("name", "mvc.errors.client.name.length");
		}
		System.out.println("Validator 5");

		if (clientDTO.getApiKey().length() < 10) {
			errors.rejectValue("apiKey", "mvc.errors.client.apiKey.length");
		}
		System.out.println("Validator 6");

		if (Arrays.asList(AccessRole.values()).stream().noneMatch(ar -> ar.toString().equals(clientDTO.getAccessRole()))) {
			errors.rejectValue("accessRole", "mvc.errors.client.accessRole.value");
		}
		System.out.println("Validator 7");

		if (Arrays.asList(ClientIntegrationType.values()).stream().noneMatch(c -> c.toString().equals(clientDTO.getIntegration()))) {
			errors.rejectValue("integration", "mvc.errors.client.integration.value");
		}
		System.out.println("Validator 8");

		if (ClientIntegrationType.valueOf(clientDTO.getIntegration()).equals(ClientIntegrationType.AD_SYNC_SERVICE)) {
			Domain domain = domainService.getByName(clientDTO.getDomain());
			System.out.println("Validator 9");
			if (domain == null) {
				errors.rejectValue("domain", "mvc.errors.client.domain.none");
			} else {
				Client clientWithDomain = clientService.getClientByDomain(domain);
				if (clientWithDomain != null && clientWithDomain.getId() != clientDTO.getId()) {
					errors.rejectValue("domain", "mvc.errors.client.domain.duplicate");
				}
			}
		}
		System.out.println("Validator 10");
	}
}
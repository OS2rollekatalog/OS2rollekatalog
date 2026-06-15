package dk.digitalidentity.rc.controller.validator;

import dk.digitalidentity.rc.controller.mvc.viewmodel.ItSystemForm;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

import java.util.List;

import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ItSystemValidator — selectedResponsibleUuid list validation")
class ItSystemValidatorTest {

    @Mock private ItSystemService itSystemService;
    @Mock private DomainService domainService;
    @Mock private UserService userService;

    @InjectMocks
    private ItSystemValidator validator;

    private static ItSystemForm baseForm() {
        ItSystemForm form = new ItSystemForm();
        form.setId(1L);
        form.setName("Test System");
        form.setIdentifier("TEST_SYSTEM");
        form.setSystemType(dk.digitalidentity.rc.dao.model.enums.ItSystemType.SAML);
        return form;
    }

    private Errors errorsFor(ItSystemForm form) {
        return new BeanPropertyBindingResult(form, "itSystemForm");
    }

    @Nested
    @DisplayName("selectedResponsibleUuid")
    class SelectedResponsibleUuid {

        @Test
        @DisplayName("passes when responsible list is null")
        void nullList_noError() {
            ItSystemForm form = baseForm();
            form.setSelectedResponsibleUuid(null);
            Errors errors = errorsFor(form);

            validator.validate(form, errors);

            assertThat(errors.hasFieldErrors("selectedResponsibleUuid")).isFalse();
        }

        @Test
        @DisplayName("passes when all UUIDs in the list resolve to existing users")
        void allValidUuids_noError() {
            User user1 = createUser("uuid-1", "user1", "Alice");
            User user2 = createUser("uuid-2", "user2", "Bob");
            ItSystemForm form = baseForm();
            form.setSelectedResponsibleUuid(List.of("uuid-1", "uuid-2"));
            Errors errors = errorsFor(form);

            when(userService.getByUuid("uuid-1")).thenReturn(user1);
            when(userService.getByUuid("uuid-2")).thenReturn(user2);

            validator.validate(form, errors);

            assertThat(errors.hasFieldErrors("selectedResponsibleUuid")).isFalse();
        }

        @Test
        @DisplayName("rejects when any UUID in the list does not resolve to a user")
        void oneUnknownUuid_rejectsField() {
            User user1 = createUser("uuid-1", "user1", "Alice");
            ItSystemForm form = baseForm();
            form.setSelectedResponsibleUuid(List.of("uuid-1", "uuid-unknown"));
            Errors errors = errorsFor(form);

            when(userService.getByUuid("uuid-1")).thenReturn(user1);
            when(userService.getByUuid("uuid-unknown")).thenReturn(null);

            validator.validate(form, errors);

            assertThat(errors.hasFieldErrors("selectedResponsibleUuid")).isTrue();
            assertThat(errors.getFieldError("selectedResponsibleUuid").getCode())
                    .isEqualTo("html.errors.itsystem.selectedResponsibleUuid.notfound");
        }
    }
}

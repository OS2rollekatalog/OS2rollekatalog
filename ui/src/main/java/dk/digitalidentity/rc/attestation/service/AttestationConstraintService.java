package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.model.dto.SystemRoleConstraintDTO;
import dk.digitalidentity.rc.dao.ItSystemDao;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AttestationConstraintService {
    @Autowired
    private ItSystemDao itSystemDao;
    @Autowired
    private OrgUnitDao orgUnitDao;
    @Autowired
    private MessageSource messageSource;

    public String caption(final SystemRoleConstraintDTO systemRoleConstraintDTO) {
        final Locale locale = LocaleContextHolder.getLocale();
        final String defaultCaption = systemRoleConstraintDTO.name() + " " + systemRoleConstraintDTO.value();
        return switch (systemRoleConstraintDTO.valueType()) {
            case VALUE -> valueCaption(systemRoleConstraintDTO, defaultCaption);
            case POSTPONED -> systemRoleConstraintDTO.name() + " udskudt";
            case READ_AND_WRITE -> readWriteCaption(systemRoleConstraintDTO, defaultCaption);
            case INHERITED -> inheritedCaption(systemRoleConstraintDTO, defaultCaption);
            case EXTENDED_INHERITED -> extendedInheritedCaption(systemRoleConstraintDTO, defaultCaption);
            case LEVEL_1 -> messageSource.getMessage("html.constraint.organisation.level.1", null, locale);
            case LEVEL_2 -> messageSource.getMessage("html.constraint.organisation.level.2", null, locale);
            case LEVEL_3 -> messageSource.getMessage("html.constraint.organisation.level.3", null, locale);
            case LEVEL_4 -> messageSource.getMessage("html.constraint.organisation.level.4", null, locale);
            case LEVEL_5 -> messageSource.getMessage("html.constraint.organisation.level.5", null, locale);
            case LEVEL_6 -> messageSource.getMessage("html.constraint.organisation.level.6", null, locale);
        };
    }

    private String extendedInheritedCaption(final SystemRoleConstraintDTO systemRoleConstraintDTO, final String defaultCaption) {
        final Locale locale = LocaleContextHolder.getLocale();
        if (systemRoleConstraintDTO.name().equalsIgnoreCase("KLE")) {
            return messageSource.getMessage("html.constraint.kle.extended", null, locale);
        } else if (systemRoleConstraintDTO.name().equalsIgnoreCase("Organisation")) {
            return messageSource.getMessage("html.constraint.organisation.extended", null, locale);
        }
        return defaultCaption;
    }

    private String inheritedCaption(final SystemRoleConstraintDTO systemRoleConstraintDTO, final String defaultCaption) {
        final Locale locale = LocaleContextHolder.getLocale();
        if (systemRoleConstraintDTO.name().equalsIgnoreCase("KLE")) {
            return messageSource.getMessage("html.constraint.kle.inherited", null, locale);
        } else if (systemRoleConstraintDTO.name().equalsIgnoreCase("Organisation")) {
            return messageSource.getMessage("html.constraint.organisation.inherited", null, locale);
        }
        return defaultCaption;
    }

    private String readWriteCaption(final SystemRoleConstraintDTO systemRoleConstraintDTO, final String defaultCaption) {
        final Locale locale = LocaleContextHolder.getLocale();
        if (systemRoleConstraintDTO.name().equalsIgnoreCase("KLE")) {
            return messageSource.getMessage("html.constraint.kle.read_and_write", null, locale);
        }
        return defaultCaption;
    }

    private String valueCaption(final SystemRoleConstraintDTO systemRoleConstraintDTO, final String defaultCaption) {
        final Locale locale = LocaleContextHolder.getLocale();
        if (systemRoleConstraintDTO.name().equalsIgnoreCase("It-system")) {
            return itSystemDao.findById(Long.valueOf(systemRoleConstraintDTO.value()))
                    .map(it -> messageSource.getMessage("html.entity.itsystem", null, locale) + ": " + it.getName())
                    .orElse(defaultCaption);
        } else if (systemRoleConstraintDTO.name().equalsIgnoreCase("Organisation")) {
            return orgUnitDao.findById(systemRoleConstraintDTO.value())
                    .map(ou -> messageSource.getMessage("html.entity.ou.type", null, locale) + ": " + ou.getName())
                    .orElse(defaultCaption);
        }
        return defaultCaption;
    }

}

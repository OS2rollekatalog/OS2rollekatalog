package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.model.dto.SystemRoleConstraintDTO;
import dk.digitalidentity.rc.dao.ItSystemDao;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
            case SELECTED_INHERITED -> selectedInheritedCaption(systemRoleConstraintDTO, defaultCaption);
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

    private String selectedInheritedCaption(final SystemRoleConstraintDTO systemRoleConstraintDTO, final String defaultCaption) {
        final Locale locale = LocaleContextHolder.getLocale();
        if (systemRoleConstraintDTO.name().equalsIgnoreCase("KLE")) {
            return messageSource.getMessage("html.constraint.kle.selected_inherited", null, locale);
        } else if (systemRoleConstraintDTO.name().equalsIgnoreCase("Organisation")) {
            return messageSource.getMessage("html.constraint.organisation.selected.inherited", null, locale);
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
            final List<String> itSystemNames = Arrays.stream(StringUtils.split(systemRoleConstraintDTO.value(), ","))
                    .map(id -> itSystemDao.findById(Long.valueOf(id)))
                    .filter(Optional::isPresent)
                    .map(it -> it.get().getName())
                    .toList();
            return itSystemNames.isEmpty()
                    ? defaultCaption
                    : messageSource.getMessage("html.entity.itsystem", null, locale) + ": " + StringUtils.join(itSystemNames, ", ");
        } else if (systemRoleConstraintDTO.name().equalsIgnoreCase("Organisation")) {
            final List<String> ous = Arrays.stream(StringUtils.split(systemRoleConstraintDTO.value(), ","))
                    .map(id -> orgUnitDao.findById(id))
                    .filter(Optional::isPresent)
                    .map(ou -> ou.get().getName())
                    .toList();
            return ous.isEmpty()
                    ? defaultCaption
                    : messageSource.getMessage("html.entity.ou.type", null, locale) + ": " + StringUtils.join(ous, ", ");
        }
        return defaultCaption;
    }

}

package dk.digitalidentity.rc.service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import dk.digitalidentity.rc.dao.model.AttachmentFile;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RoleNotificationService {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	@Autowired
	private OrgUnitService orgUnitService;
	
	@Autowired
	private PositionService positionService;
	
	@Autowired
	private TemplateEngine templateEngine;
	
	@Autowired
	private EmailQueueService emailQueueService;
	
	@Autowired
	private EmailTemplateService emailTemplateService;

	@Autowired
	private ManagerSubstituteService managerSubstituteService;

	public record Recipient(String name, String email) {}
	public record ExpiringRoleReport(String orgUnitName, List<String> expiringStrings, List<Recipient> recipients) {}
	public record RenderedReport(ExpiringRoleReport report, byte[] pdf) {}
	
    @Transactional(readOnly = true)
    public List<ExpiringRoleReport> collectReports() {
        EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.ROLE_EXPIRING);
        if (!template.isEnabled()) {
            log.info("Email template with type " + template.getTemplateType() + " is disabled. Email was not sent.");
            return List.of();
        }

        List<OrgUnit> ous = orgUnitService.getAll();
        LocalDate modifiedDate = LocalDate.now().plusDays(14);
        List<ExpiringRoleReport> reports = new ArrayList<>();

        for (OrgUnit ou : ous) {
            User manager = ou.getManager();
            if (manager == null) {
                continue;
            }

            List<Recipient> recipients = new ArrayList<>();
            for (User substitute : managerSubstituteService.getSubstitutesForOrgUnit(ou)) {
                if (!substitute.isDeleted() && StringUtils.hasLength(substitute.getEmail())) {
                    recipients.add(new Recipient(substitute.getName(), substitute.getEmail()));
                }
            }
            if (StringUtils.hasLength(manager.getEmail())) {
                recipients.add(new Recipient(manager.getName(), manager.getEmail()));
            }

            if (recipients.isEmpty()) {
                continue;
            }

            List<String> expiringStrings = new ArrayList<>();

            // assignments with stopDate within 14 days for orgUnit
            for (OrgUnitRoleGroupAssignment ourga : ou.getRoleGroupAssignments()) {
                if (ourga.getStopDate() != null && ourga.getStopDate().isBefore(modifiedDate)) {
                    expiringStrings.add("Rollebuketten " + ourga.getRoleGroup().getName()
                        + " tildelt på enheden " + ou.getName() + " udløber " + ourga.getStopDate());
                }
            }

            for (OrgUnitUserRoleAssignment ouura : ou.getUserRoleAssignments()) {
                if (ouura.getStopDate() != null && ouura.getStopDate().isBefore(modifiedDate)) {
                    expiringStrings.add("Jobfunktionsrollen " + ouura.getUserRole().getName()
                        + " (" + ouura.getUserRole().getItSystem().getName() + ") tildelt på enheden "
                        + ou.getName() + " udløber " + ouura.getStopDate());
                }
            }

            // titles associated with the OU
            for (OrgUnitUserRoleAssignment oura : ou.getUserRoleAssignments()) {
                if (oura.getContainsTitles() == ContainsTitles.NO) {
                    continue;
                }
                if (oura.getStopDate() != null && oura.getStopDate().isBefore(modifiedDate)) {
                    for (Title title : oura.getTitles()) {
                        expiringStrings.add("Jobfunktionsrollen " + oura.getUserRole().getName()
                            + " (" + oura.getUserRole().getItSystem().getName() + ") tildelt på titlen "
                            + title.getName() + " associeret med enheden " + ou.getName()
                            + " udløber " + oura.getStopDate());
                    }
                }
            }

            for (OrgUnitRoleGroupAssignment orga : ou.getRoleGroupAssignments()) {
                if (orga.getContainsTitles() == ContainsTitles.NO) {
                    continue;
                }
                if (orga.getStopDate() != null && orga.getStopDate().isBefore(modifiedDate)) {
                    for (Title title : orga.getTitles()) {
                        expiringStrings.add("Rollebuketten " + orga.getRoleGroup().getName()
                            + " tildelt på titlen " + title.getName() + " associeret med enheden "
                            + ou.getName() + " udløber " + orga.getStopDate());
                    }
                }
            }

            // positions associated with the OU
            for (Position position : positionService.findByOrgUnit(ou)) {
                User user = position.getUser();
                if (user == null || user.isDeleted()) {
                    continue;
                }

                for (UserUserRoleAssignment uura : user.getUserRoleAssignments()) {
                    if (uura.getStopDate() != null && uura.getStopDate().isBefore(modifiedDate)) {
                        expiringStrings.add("Jobfunktionsrollen " + uura.getUserRole().getName()
                            + " (" + uura.getUserRole().getItSystem().getName() + ") tildelt brugeren "
                            + user.getName() + " udløber " + uura.getStopDate());
                    }
                }

                for (UserRoleGroupAssignment urga : user.getRoleGroupAssignments()) {
                    if (urga.getStopDate() != null && urga.getStopDate().isBefore(modifiedDate)) {
                        expiringStrings.add("Rollebuketten " + urga.getRoleGroup().getName()
                            + " tildelt brugeren " + user.getName() + " udløber " + urga.getStopDate());
                    }
                }
            }

            if (!expiringStrings.isEmpty()) {
                reports.add(new ExpiringRoleReport(ou.getName(), expiringStrings, recipients));
            }
        }

        return reports;
    }

    public List<RenderedReport> render(List<ExpiringRoleReport> reports) {
        String now = LocalDateTime.now().format(TIMESTAMP);

        return reports.parallelStream()
            .map(report -> {
                Context ctx = new Context();
                ctx.setVariable("strings", report.expiringStrings());
                ctx.setVariable("ou", report.orgUnitName());
                ctx.setVariable("time", now);

                String html = templateEngine.process("manager/expiring_roles_pdf", ctx);

                try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    ITextRenderer renderer = new ITextRenderer();
                    renderer.setDocumentFromString(html);
                    renderer.layout();
                    renderer.createPDF(out);
                    return new RenderedReport(report, out.toByteArray());
                }
                catch (Exception ex) {
                    throw new IllegalStateException("Failed rendering PDF for " + report.orgUnitName(), ex);
                }
            })
            .toList();
    }

    @Transactional
    public void queueEmails(List<RenderedReport> rendered) {
        EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.ROLE_EXPIRING);
        if (!template.isEnabled()) {
            return;
        }

        for (RenderedReport r : rendered) {
            for (Recipient recipient : r.report().recipients()) {
                AttachmentFile attachment = new AttachmentFile();
                attachment.setContent(r.pdf());
                attachment.setFilename("Rettighedsudløb.pdf");

                String title = fill(template.getTitle(), recipient.name(), r.report().orgUnitName());
                String message = fill(template.getMessage(), recipient.name(), r.report().orgUnitName());

                emailQueueService.queueEmail(recipient.email(), title, message, template, new ArrayList<>(List.of(attachment)), null);
            }
        }
    }

    private String fill(String text, String receiverName, String orgUnitName) {
        return text
            .replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), receiverName)
            .replace(EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER.getPlaceholder(), orgUnitName);
    }
}

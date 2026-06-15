package dk.digitalidentity.rc.dao.model.enums;

import java.util.List;

/**
 * Declares that an email template type has a repeating part: a template fragment expanded once per row
 * and inserted where the trigger placeholder appears in the parent text. A repeating part may itself
 * declare a nested repeating part, expanded per sub-row inside each row.
 *
 * The list structure around a repeating part is owned here, not in the stored template: {@code itemTag}
 * wraps each rendered row and {@code containerTag} wraps the whole expanded group. This keeps structural
 * markup (e.g. {@code <ul>}/{@code <li>}) out of the editable template, so the repeating parts can be
 * edited as plain content in the WYSIWYG editor. Either tag may be null to add no wrapper at that level
 * (e.g. the per-user block of the it-system mail is a free-form block, not a list item).
 */
public record RepeatingPartDescriptor(EmailTemplatePlaceholder trigger, List<EmailTemplatePlaceholder> placeholders, String containerTag, String itemTag, RepeatingPartDescriptor nested) {

	public RepeatingPartDescriptor {
		// storage (email_templates.repeating_part/nested_repeating_part) and the renderer support exactly two levels
		if (nested != null && nested.nested() != null) {
			throw new IllegalArgumentException("at most two repeating levels are supported");
		}
	}

	public RepeatingPartDescriptor(EmailTemplatePlaceholder trigger, List<EmailTemplatePlaceholder> placeholders, String containerTag, String itemTag) {
		this(trigger, placeholders, containerTag, itemTag, null);
	}

	/** Wraps a single rendered row in {@code itemTag} (no-op when itemTag is null). */
	public String wrapItem(String renderedRow) {
		return itemTag == null ? renderedRow : "<" + itemTag + ">" + renderedRow + "</" + itemTag + ">";
	}

	/** Wraps the joined rows in {@code containerTag} (no-op when containerTag is null). */
	public String wrapGroup(String renderedGroup) {
		return containerTag == null ? renderedGroup : "<" + containerTag + ">" + renderedGroup + "</" + containerTag + ">";
	}
}

package dk.digitalidentity.rc.service;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import dk.digitalidentity.rc.dao.model.enums.RepeatingPartDescriptor;

/**
 * Renders email templates with placeholder substitution, including templates with repeating parts
 * (see {@link RepeatingPartDescriptor}): the repeating part is rendered once per row and the joined
 * result replaces the trigger placeholder in the parent text. Rows may carry sub-rows, rendered
 * against the nested repeating part the same way.
 *
 * Substitution is single-pass: substituted values are inserted verbatim and never re-scanned, so
 * placeholder tokens occurring inside data values (e.g. a role description containing "{tildeler}")
 * stay literal. Null values render as the empty string - callers wanting other null behavior must
 * map their values before rendering.
 *
 * The list structure around a repeating part (e.g. {@code <ul>}/{@code <li>}) is applied here from the
 * descriptor's item/container tags, not stored in the template - so the editable repeating parts hold
 * plain content only. See {@link RepeatingPartDescriptor}.
 */
@Component
public class EmailTemplateRenderer {

	// matches {navn} and {navn:N}; N capped at 9 digits so Integer.parseInt cannot overflow
	private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{([^{}:]+)(?::(\\d{1,9}))?\\}");

	/**
	 * One row of data for a repeating part. subRows are only used when the descriptor declares a nested part.
	 */
	public record Row(Map<EmailTemplatePlaceholder, String> values, List<Row> subRows) {

		public Row(Map<EmailTemplatePlaceholder, String> values) {
			this(values, List.of());
		}
	}

	public String renderTitle(EmailTemplate template, Map<EmailTemplatePlaceholder, String> values) {
		return render(template.getTitle(), values);
	}

	public String renderMessage(EmailTemplate template, Map<EmailTemplatePlaceholder, String> values, List<Row> rows) {
		RepeatingPartDescriptor descriptor = template.getTemplateType().getRepeatingPart();
		if (descriptor == null) {
			return render(template.getMessage(), values);
		}

		StringBuilder expanded = new StringBuilder();
		for (Row row : rows) {
			Map<EmailTemplatePlaceholder, String> rowValues = row.values();

			if (descriptor.nested() != null) {
				RepeatingPartDescriptor nested = descriptor.nested();
				StringBuilder nestedExpanded = new StringBuilder();
				for (Row subRow : row.subRows()) {
					nestedExpanded.append(nested.wrapItem(render(template.getNestedRepeatingPart(), subRow.values())));
				}
				rowValues = withValue(rowValues, nested.trigger(), nested.wrapGroup(nestedExpanded.toString()));
			}

			expanded.append(descriptor.wrapItem(render(template.getRepeatingPart(), rowValues)));
		}

		return render(template.getMessage(), withValue(values, descriptor.trigger(), descriptor.wrapGroup(expanded.toString())));
	}

	public String render(String text, Map<EmailTemplatePlaceholder, String> values) {
		if (text == null) {
			return "";
		}

		Map<String, EmailTemplatePlaceholder> byName = new HashMap<>();
		for (EmailTemplatePlaceholder placeholder : values.keySet()) {
			String token = placeholder.getPlaceholder();
			byName.put(token.substring(1, token.length() - 1), placeholder);
		}

		Matcher matcher = TOKEN_PATTERN.matcher(text);
		StringBuilder sb = new StringBuilder();
		while (matcher.find()) {
			EmailTemplatePlaceholder placeholder = byName.get(matcher.group(1));

			// unknown placeholders and :N parameters on non-parameterized placeholders stay literal
			if (placeholder == null || (matcher.group(2) != null && !placeholder.isParameterized())) {
				matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
				continue;
			}

			String value = values.get(placeholder) != null ? values.get(placeholder) : "";
			if (matcher.group(2) != null) {
				value = truncate(value, Integer.parseInt(matcher.group(2)));
			}

			matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
		}
		matcher.appendTail(sb);

		return sb.toString();
	}

	private static Map<EmailTemplatePlaceholder, String> withValue(Map<EmailTemplatePlaceholder, String> values, EmailTemplatePlaceholder placeholder, String value) {
		Map<EmailTemplatePlaceholder, String> copy = new EnumMap<>(EmailTemplatePlaceholder.class);
		copy.putAll(values);
		copy.put(placeholder, value);
		return copy;
	}

	private static String truncate(String value, int maxLength) {
		String firstLine = value.split("\\R|<br\\s*/?>", 2)[0];

		return firstLine.length() <= maxLength ? firstLine : firstLine.substring(0, maxLength);
	}
}

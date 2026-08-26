UPDATE email_templates
SET nested_repeating_part = CONCAT(nested_repeating_part, '{afgrænsninger}')
WHERE template_type IN ('MANUAL_SYSTEM_CONTACT_PERFORMER', 'MANUAL_SYSTEM_CONTACT_ADVIS')
  AND nested_repeating_part = '{handling} rolle: {rolle} ({rollebeskrivelse}), {handlet} af {tildeler}';

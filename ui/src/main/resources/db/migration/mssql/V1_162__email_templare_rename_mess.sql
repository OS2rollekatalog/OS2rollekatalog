DELETE FROM email_templates WHERE id IN (SELECT et.id FROM email_templates et
                                         INNER JOIN email_templates et2 ON et2.template_type='ATTESTATION_REMINDER_10_DAYS'
                                         WHERE et.template_type = 'ATTESTATION_REMINDER1');
UPDATE email_templates set template_type= 'ATTESTATION_REMINDER1' where template_type= 'ATTESTATION_REMINDER_10_DAYS';

DELETE FROM email_templates WHERE id IN (SELECT et.id FROM email_templates et
                                         INNER JOIN email_templates et2 ON et2.template_type='ATTESTATION_REMINDER_3_DAYS'
                                         WHERE et.template_type = 'ATTESTATION_REMINDER2');
UPDATE email_templates set template_type= 'ATTESTATION_REMINDER2' where template_type= 'ATTESTATION_REMINDER_3_DAYS';

DELETE FROM email_templates WHERE id IN (SELECT et.id FROM email_templates et
                                         INNER JOIN email_templates et2 ON et2.template_type='ATTESTATION_SENSITIVE_REMINDER_10_DAYS'
                                         WHERE et.template_type = 'ATTESTATION_SENSITIVE_REMINDER1');
UPDATE email_templates set template_type= 'ATTESTATION_SENSITIVE_REMINDER1' where template_type= 'ATTESTATION_SENSITIVE_REMINDER_10_DAYS';

DELETE FROM email_templates WHERE id IN (SELECT et.id FROM email_templates et
                                         INNER JOIN email_templates et2 ON et2.template_type='ATTESTATION_SENSITIVE_REMINDER_3_DAYS'
                                         WHERE et.template_type = 'ATTESTATION_SENSITIVE_REMINDER2');
UPDATE email_templates set template_type= 'ATTESTATION_SENSITIVE_REMINDER2' where template_type= 'ATTESTATION_SENSITIVE_REMINDER_3_DAYS';

DELETE FROM email_templates WHERE id IN (SELECT et.id FROM email_templates et
                                         INNER JOIN email_templates et2 ON et2.template_type='ATTESTATION_IT_SYSTEM_REMINDER_10_DAYS'
                                         WHERE et.template_type = 'ATTESTATION_IT_SYSTEM_REMINDER1');
UPDATE email_templates set template_type= 'ATTESTATION_IT_SYSTEM_REMINDER1' where template_type= 'ATTESTATION_IT_SYSTEM_REMINDER_10_DAYS';

DELETE FROM email_templates WHERE id IN (SELECT et.id FROM email_templates et
                                         INNER JOIN email_templates et2 ON et2.template_type='ATTESTATION_IT_SYSTEM_REMINDER_3_DAYS'
                                         WHERE et.template_type = 'ATTESTATION_IT_SYSTEM_REMINDER2');
UPDATE email_templates set template_type= 'ATTESTATION_IT_SYSTEM_REMINDER2' where template_type= 'ATTESTATION_IT_SYSTEM_REMINDER_3_DAYS';

UPDATE attestation_mail set email_template_type= 'ATTESTATION_REMINDER1' where email_template_type= 'ATTESTATION_REMINDER_10_DAYS';
UPDATE attestation_mail set email_template_type= 'ATTESTATION_REMINDER2' where email_template_type= 'ATTESTATION_REMINDER_3_DAYS';
UPDATE attestation_mail set email_template_type= 'ATTESTATION_SENSITIVE_REMINDER1' where email_template_type= 'ATTESTATION_SENSITIVE_REMINDER_10_DAYS';
UPDATE attestation_mail set email_template_type= 'ATTESTATION_SENSITIVE_REMINDER2' where email_template_type= 'ATTESTATION_SENSITIVE_REMINDER_3_DAYS';
UPDATE attestation_mail set email_template_type= 'ATTESTATION_IT_SYSTEM_REMINDER1' where email_template_type= 'ATTESTATION_IT_SYSTEM_REMINDER_10_DAYS';
UPDATE attestation_mail set email_template_type= 'ATTESTATION_IT_SYSTEM_REMINDER2' where email_template_type= 'ATTESTATION_IT_SYSTEM_REMINDER_3_DAYS';


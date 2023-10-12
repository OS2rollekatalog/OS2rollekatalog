DROP TABLE attestation_notifications;
UPDATE setting SET setting_value='true' where setting_key='ScheduledAttestationEnabled';
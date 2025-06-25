TRUNCATE security_log;
ALTER TABLE security_log ADD INDEX security_log_tts_idx (timestamp);

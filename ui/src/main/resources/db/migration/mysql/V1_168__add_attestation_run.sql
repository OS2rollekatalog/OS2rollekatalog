alter table attestation_mail
    drop foreign key fk_am_attestation_id;
alter table attestation_mail
    add constraint fk_am_attestation_id
        foreign key (attestation_id) references attestation_attestation (id)
            on delete cascade;

CREATE TABLE attestation_run
(
    id              BIGINT AUTO_INCREMENT NOT NULL,
    created_at      date                  NULL,
    `sensitive`     BIT(1)                NULL,
    super_sensitive BIT(1)                NULL,
    finished        BIT(1)                NULL,
    deadline        date                  NULL,
    CONSTRAINT pk_attestation_run PRIMARY KEY (id)
);

ALTER TABLE attestation_attestation
    ADD attestation_run_id BIGINT NULL AFTER created_at;
ALTER TABLE attestation_attestation
    ADD CONSTRAINT FK_ATTESTATION_ATTESTATION_ON_ATTESTATION_RUN FOREIGN KEY (attestation_run_id)
        REFERENCES attestation_run (id) ON DELETE CASCADE;

CREATE TABLE attestation_mail_receiver
(
    id            BIGINT AUTO_INCREMENT NOT NULL,
    receiver_type VARCHAR(10)           NULL,
    email         VARCHAR(255)          NULL,
    user_uuid     VARCHAR(36)           NULL,
    title         VARCHAR(255)          NULL,
    message       MEDIUMTEXT            NULL,
    mail_id       BIGINT                NOT NULL,
    CONSTRAINT pk_attestation_mail_receiver PRIMARY KEY (id)
);

ALTER TABLE attestation_mail_receiver
    ADD CONSTRAINT FK_ATTESTATION_MAIL_RECEIVER_ON_MAIL FOREIGN KEY (mail_id) REFERENCES attestation_mail (id) ON DELETE CASCADE;

alter table attestation_mail
    drop constraint fk_am_attestation_id
    go

alter table attestation_mail
    add constraint fk_am_attestation_id
        foreign key (attestation_id) references attestation_attestation
            on delete cascade
    go

CREATE TABLE attestation_run
(
    id              bigint IDENTITY (1, 1) NOT NULL,
    created_at      date,
    sensitive       bit,
    super_sensitive bit,
    deadline        date,
    finished        bit,
    CONSTRAINT pk_attestation_run PRIMARY KEY (id)
)
    GO

alter table dbo.attestation_attestation
    add attestation_run_id bigint
    go

alter table dbo.attestation_attestation
    add constraint FK_ATTESTATION_ATTESTATION_ON_ATTESTATION_RUN
        foreign key (attestation_run_id) references dbo.attestation_run (id)
            on delete cascade
    go

CREATE TABLE attestation_mail_receiver
(
    id            bigint IDENTITY (1, 1) NOT NULL,
    receiver_type varchar(10),
    email         varchar(255),
    user_uuid     varchar(36),
    title         varchar(255),
    message       varchar(255),
    mail_id       bigint                 NOT NULL,
    CONSTRAINT pk_attestation_mail_receiver PRIMARY KEY (id)
)
    GO

ALTER TABLE attestation_mail_receiver
    ADD CONSTRAINT FK_ATTESTATION_MAIL_RECEIVER_ON_MAIL FOREIGN KEY (mail_id) REFERENCES attestation_mail (id) ON DELETE CASCADE
    GO
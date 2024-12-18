ALTER TABLE client ADD client_integration_type varchar(36) NOT NULL DEFAULT 'GENERIC'
GO
ALTER TABLE client ADD domain_id bigint
GO
ALTER TABLE client ADD CONSTRAINT FK_CLIENT_ON_DOMAIN FOREIGN KEY (domain_id) REFERENCES domains (id)
GO

CREATE TABLE ad_configuration (
  id bigint IDENTITY (1, 1) NOT NULL,
   version int NOT NULL,
   updated_at datetime NOT NULL,
   updated_by varchar(255) NOT NULL,
   error_message text NULL,
   json NVARCHAR(MAX),
   client_id bigint NOT NULL,
   CONSTRAINT pk_ad_configuration PRIMARY KEY (id)
)
GO
ALTER TABLE ad_configuration ADD CONSTRAINT UQ_VERSION_CLIENT UNIQUE (version, client_id)
GO
ALTER TABLE ad_configuration ADD CONSTRAINT FK_AD_CONFIGURATION_ON_CLIENT FOREIGN KEY (client_id) REFERENCES client (id)
GO
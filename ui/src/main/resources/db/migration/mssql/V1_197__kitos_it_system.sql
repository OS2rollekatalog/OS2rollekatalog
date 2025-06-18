CREATE TABLE kitos_it_system (
  id bigint IDENTITY (1, 1) NOT NULL,
   kitos_uuid uniqueidentifier NOT NULL,
   name varchar(255) NOT NULL,
   CONSTRAINT pk_kitos_it_system PRIMARY KEY (id)
)
GO
CREATE TABLE kitos_it_system_user (
  id bigint IDENTITY (1, 1) NOT NULL,
   kitos_uuid uniqueidentifier NOT NULL,
   name varchar(255) NOT NULL,
   email varchar(255) NOT NULL,
   role varchar(255) NOT NULL,
   kitos_it_system_id bigint NOT NULL,
   CONSTRAINT pk_kitos_it_system_user PRIMARY KEY (id)
)
GO

ALTER TABLE kitos_it_system_user ADD CONSTRAINT FK_KITOS_IT_SYSTEM_USER_ON_KITOS_IT_SYSTEM FOREIGN KEY (kitos_it_system_id) REFERENCES kitos_it_system (id) ON DELETE CASCADE
GO
ALTER TABLE it_systems ADD kitos_it_system_id bigint
GO
ALTER TABLE it_systems ADD CONSTRAINT FK_IT_SYSTEM_KITOS_IT_SYSTEM FOREIGN KEY (kitos_it_system_id) REFERENCES kitos_it_system (id) ON DELETE SET NULL
GO
CREATE TABLE kitos_it_system (
  id BIGINT AUTO_INCREMENT NOT NULL,
   kitos_uuid BINARY(16) NOT NULL,
   name VARCHAR(255) NOT NULL,
   CONSTRAINT pk_kitos_it_system PRIMARY KEY (id)
);

CREATE TABLE kitos_it_system_user (
  id BIGINT AUTO_INCREMENT NOT NULL,
   kitos_uuid BINARY(16) NOT NULL,
   name VARCHAR(255) NOT NULL,
   email VARCHAR(255) NOT NULL,
   `role` VARCHAR(255) NOT NULL,
   kitos_it_system_id BIGINT NOT NULL,
   CONSTRAINT pk_kitos_it_system_user PRIMARY KEY (id)
);

ALTER TABLE kitos_it_system_user ADD CONSTRAINT FK_KITOS_IT_SYSTEM_USER_ON_KITOS_IT_SYSTEM FOREIGN KEY (kitos_it_system_id) REFERENCES kitos_it_system (id) ON DELETE CASCADE;
ALTER TABLE it_systems ADD kitos_it_system_id BIGINT NULL;
ALTER TABLE it_systems ADD CONSTRAINT FK_IT_SYSTEM_KITOS_IT_SYSTEM FOREIGN KEY (kitos_it_system_id) REFERENCES kitos_it_system (id) ON DELETE SET NULL;
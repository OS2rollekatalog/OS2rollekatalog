CREATE TABLE front_page_links (
  id                              BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  icon                            NVARCHAR(64) NOT NULL,
  title                           NVARCHAR(64) NOT NULL,
  description                     NTEXT NOT NULL,
  link                            NTEXT NOT NULL,
  active                          SMALLINT NOT NULL DEFAULT 1,
  editable                        SMALLINT NOT NULL DEFAULT 1
);

INSERT INTO front_page_links (icon, title, description, link, active, editable) VALUES ('fa-sitemap','Jeg er leder','Klik her hvis du er leder eller autorisationsansvarlig, og skal administrere dine medarbejdere','/ui/my',1,0);
INSERT INTO front_page_links (icon, title, description, link, active, editable) VALUES ('fa-gears','Jeg er tekniker','Klik her hvis du er tekniker, og skal tilgå dokumentation, opsætning eller hente integrationskomponenter til rollekataloget','/info',1,0);
INSERT INTO front_page_links (icon, title, description, link, active, editable) VALUES ('fa-pencil','Jeg er rolleadministrator','Klik her hvis du skal ind og anvende rollekataloget til at opsætte, tildele eller administrere adgange','/ui/users/list',1,0);
INSERT INTO front_page_links (icon, title, description, link, active, editable) VALUES ('fa-check','Attestering og rettighedskontrol','Klik her hvis du er leder, stedfortræder, systemansvarlig eller administrator og skal attestere eller tjekke status på attestering','/ui/attestation/v2',1,0);
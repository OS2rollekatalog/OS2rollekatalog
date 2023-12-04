CREATE TABLE front_page_links (
  id                              BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  icon                            VARCHAR(64) NOT NULL,
  title                           VARCHAR(64) NOT NULL,
  description                     TEXT NOT NULL,
  link                            TEXT NOT NULL,
  active                          BOOLEAN NOT NULL DEFAULT TRUE,
  editable                        BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO front_page_links (icon, title, description, link, active, editable) VALUES ('fa-sitemap','Jeg er leder','Klik her hvis du er leder eller autorisationsansvarlig, og skal administrere dine medarbejdere','/ui/my',true,false);
INSERT INTO front_page_links (icon, title, description, link, active, editable) VALUES ('fa-gears','Jeg er tekniker','Klik her hvis du er tekniker, og skal tilgå dokumentation, opsætning eller hente integrationskomponenter til rollekataloget','/info',true,false);
INSERT INTO front_page_links (icon, title, description, link, active, editable) VALUES ('fa-pencil','Jeg er rolleadministrator','Klik her hvis du skal ind og anvende rollekataloget til at opsætte, tildele eller administrere adgange','/ui/users/list',true,false);
INSERT INTO front_page_links (icon, title, description, link, active, editable) VALUES ('fa-check','Attestering og rettighedskontrol','Klik her hvis du er leder, stedfortræder, systemansvarlig eller administrator og skal attestere eller tjekke status på attestering','/ui/attestation/v2',true,false);
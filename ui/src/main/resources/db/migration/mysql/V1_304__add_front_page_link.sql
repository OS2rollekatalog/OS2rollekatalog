ALTER TABLE front_page_links ADD COLUMN deletable BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE front_page_links SET deletable = editable;
INSERT INTO front_page_links (icon, title, description, link, active, editable, deletable) VALUES ('fa-handshake-o', 'Anmod og godkend', 'Klik her hvis du vil se dine nuværende rettigheder eller anmode om nye, hvis du er leder eller autorisationsansvarlig er det desuden her du godkender anmodninger.', '/ui/request', 0, 1, 0);

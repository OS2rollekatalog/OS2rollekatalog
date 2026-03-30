ALTER TABLE front_page_links ADD COLUMN sort_order INT NOT NULL DEFAULT 0;

UPDATE front_page_links l1
SET sort_order = (
    SELECT COUNT(*)
    FROM front_page_links l2
    WHERE l2.link_type = l1.link_type
      AND l2.id <= l1.id
);
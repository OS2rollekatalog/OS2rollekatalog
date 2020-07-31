ALTER TABLE report_template ADD COLUMN show_titles VARCHAR(6);
UPDATE report_template SET show_titles = 0;

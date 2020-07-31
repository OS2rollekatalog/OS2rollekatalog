ALTER TABLE report_template ADD show_titles NVARCHAR(6);
GO
UPDATE report_template SET show_titles = 0;

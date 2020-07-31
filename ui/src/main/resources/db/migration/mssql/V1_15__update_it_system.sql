ALTER TABLE it_systems ADD notes NTEXT;

-- cannot drop this due to automatically created constraints, so we will leave it be
-- ALTER TABLE it_systems DROP COLUMN expand_to_bsr;
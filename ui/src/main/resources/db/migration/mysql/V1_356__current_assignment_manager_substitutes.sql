ALTER TABLE current_assignment ADD COLUMN manager BIT(1) NOT NULL DEFAULT 0;
ALTER TABLE current_assignment ADD COLUMN substitutes BIT(1) NOT NULL DEFAULT 0;

-- Exclusion lists and inheritance could contradict each other on existing assignments, because the
-- rule engine used to decide inheritance from hardcoded assumptions in the exclusion rules instead
-- of the assignment's own inherit flag. The flag is now read, so the rows where it does not match
-- the behaviour users have seen so far are normalized here.
--
-- 1) Excluded users combined with inheritance: the combination is rejected on every write path
--    today, but older versions silently kept inherit when excluded users were added to an
--    inheriting assignment. The rules have always treated such assignments as non-inheriting (own
--    org unit only), so inherit is set to 0. Without this the role would spread to the whole
--    subtree.
UPDATE ou_roles      SET inherit = 0 WHERE inherit = 1 AND contains_excepted_users = 1;
UPDATE ou_rolegroups SET inherit = 0 WHERE inherit = 1 AND contains_excepted_users = 1;

-- 2) Excepted org units without inheritance: the feature only exists as "inherit with excepted org
--    units", and the rules have always inherited such assignments regardless of the flag. Updating
--    an assignment could however set inherit = 0 and leave the exceptions behind. Inherit is set to
--    1 so the sub org units keep the role. Without this the role would disappear from them.
UPDATE ou_roles      SET inherit = 1 WHERE inherit = 0 AND contains_excepted_ous = 1;
UPDATE ou_rolegroups SET inherit = 1 WHERE inherit = 0 AND contains_excepted_ous = 1;

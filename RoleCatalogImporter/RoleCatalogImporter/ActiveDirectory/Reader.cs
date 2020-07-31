using System;
using System.Collections.Generic;
using System.DirectoryServices;

namespace RoleCatalogImporter
{
    class Reader
    {
        private string[] ousToIgnore = new string[0];
        private string SAMAccountPrefix = null;

        public Reader()
        {
            ousToIgnore = Properties.Settings.Default.OUsToIgnore.Split(';');
            SAMAccountPrefix = Properties.Settings.Default.SAMAccountPrefix;
        }

        public List<ADOrgUnit> ReadOrgUnits()
        {
            List<ADOrgUnit> orgUnits = new List<ADOrgUnit>();

            using (DirectoryEntry startingPoint = new DirectoryEntry(Properties.Settings.Default.ADUrl))
            {
                using (DirectorySearcher searcher = new DirectorySearcher(startingPoint))
                {
                    searcher.PageSize = 500;
                    searcher.Filter = "(objectCategory=organizationalUnit)";
                    searcher.PropertiesToLoad.Add("objectGUID");
                    searcher.PropertiesToLoad.Add("name");
                    searcher.PropertiesToLoad.Add("ou");
                    searcher.PropertiesToLoad.Add(Properties.Settings.Default.OrgUnitNameField);
                    searcher.PropertiesToLoad.Add("distinguishedname");

                    using (var resultSet = searcher.FindAll())
                    {
                        foreach (SearchResult res in resultSet)
                        {
                            Guid uuid = new Guid((byte[])res.Properties["objectGUID"][0]);
                            string dn = (string)res.Properties["distinguishedname"][0];
                            string name;
                            if (res.Properties.Contains(Properties.Settings.Default.OrgUnitNameField))
                            {
                                name = (string)res.Properties[Properties.Settings.Default.OrgUnitNameField][0];
                            }
                            else if (res.Properties.Contains("name"))
                            {
                                name = (string)res.Properties["name"][0];
                            }
                            else
                            {
                                name = (string)res.Properties["ou"][0];
                            }

                            var parent = res.GetDirectoryEntry()?.Parent;

                            bool skip = false;
                            foreach (string ouToIgnore in ousToIgnore)
                            {
                                if (ouToIgnore.Trim().Length == 0)
                                {
                                    continue;
                                }

                                if (dn.ToLower().EndsWith(ouToIgnore.ToLower()))
                                {
                                    skip = true;
                                }
                            }

                            if (skip)
                            {
                                continue;
                            }

                            ADOrgUnit ou = new ADOrgUnit();
                            ou.Uuid = uuid.ToString().ToLower();
                            ou.Name = name;
                            ou.Dn = dn;
                            if (parent?.Guid != null) {
	                            ou.ParentUUID = parent.Guid.ToString().ToLower();
                            }

                            orgUnits.Add(ou);
                        }
                    }
                }
            }

            return orgUnits;
        }

        public List<ADUser> ReadUsers()
        {
            List<ADUser> users = new List<ADUser>();

            using (DirectoryEntry startingPoint = new DirectoryEntry(Properties.Settings.Default.ADUrl))
            {
                using (DirectorySearcher searcher = new DirectorySearcher(startingPoint))
                {
                    searcher.PageSize = 500;
                    searcher.Filter = "(&(objectClass=user)(objectCategory=person)(!(UserAccountControl:1.2.840.113556.1.4.803:=2)))";
                    searcher.PropertiesToLoad.Add(Properties.Settings.Default.UserTitleField);
                    searcher.PropertiesToLoad.Add("objectGUID");
                    searcher.PropertiesToLoad.Add(Properties.Settings.Default.UserNameField);
                    searcher.PropertiesToLoad.Add("distinguishedname");
                    searcher.PropertiesToLoad.Add("sAMAccountName");

                    if (!string.IsNullOrEmpty(Properties.Settings.Default.UserEmailField))
                    {
                        searcher.PropertiesToLoad.Add(Properties.Settings.Default.UserEmailField);
                    }

                    if (!string.IsNullOrEmpty(Properties.Settings.Default.UserCprField))
                    {
                        searcher.PropertiesToLoad.Add(Properties.Settings.Default.UserCprField);
                    }

                    using (var resultSet = searcher.FindAll())
                    {
                        foreach (SearchResult res in resultSet)
                        {
                            Guid uuid = new Guid((byte[])res.Properties["objectGUID"][0]);
                            string dn = (string)res.Properties["distinguishedname"][0];
                            string name = (string)res.Properties[Properties.Settings.Default.UserNameField][0];
                            string userId = (string)res.Properties["sAMAccountName"][0];
                            string title = Properties.Settings.Default.DefaultTitle;
                            if (res.Properties.Contains(Properties.Settings.Default.UserTitleField))
                            {
                                title = (string)res.Properties[Properties.Settings.Default.UserTitleField][0];
                            }

                            string email = null;
                            if (res.Properties.Contains(Properties.Settings.Default.UserEmailField))
                            {
                                email = (string)res.Properties[Properties.Settings.Default.UserEmailField][0];
                            }

                            string cpr = null;
                            if (res.Properties.Contains(Properties.Settings.Default.UserCprField))
                            {
                                cpr = (string)res.Properties[Properties.Settings.Default.UserCprField][0];

                                cpr = cpr.Replace("-", "");
                                if (cpr.Length != 10)
                                {
                                    cpr = null;
                                }
                            }

                            bool skip = false;

                            if (!string.IsNullOrEmpty(SAMAccountPrefix))
                            {
                                if (!userId.StartsWith(SAMAccountPrefix))
                                {
                                    skip = true;
                                }
                            }

                            foreach (string ouToIgnore in ousToIgnore)
                            {
                                if (ouToIgnore.Trim().Length == 0)
                                {
                                    continue;
                                }

                                if (dn.ToLower().EndsWith(ouToIgnore.ToLower()))
                                {
                                    skip = true;
                                }
                            }

                            if (skip)
                            {
                                continue;
                            }

                            ADUser user = new ADUser();
                            user.Dn = dn;
                            user.Title = title;
                            user.Name = name;
                            user.Uuid = uuid.ToString().ToLower();
                            user.UserId = userId;
                            user.Cpr = cpr;
                            user.Email = email;

                            users.Add(user);
                        }
                    }
                }
            }

            return users;
        }
    }
}

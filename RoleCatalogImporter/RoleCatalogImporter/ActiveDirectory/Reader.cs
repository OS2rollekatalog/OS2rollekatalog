using System;
using System.Collections.Generic;
using System.DirectoryServices;
using System.DirectoryServices.AccountManagement;
using System.Linq;

namespace RoleCatalogImporter
{
    class Reader
    {
        private static readonly log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private string[] ousToIgnore = new string[0];
        private string SAMAccountPrefix = null;
        private string UserFilter = null;

        public Reader()
        {
            ousToIgnore = Properties.Settings.Default.OUsToIgnore.Split(';');
            SAMAccountPrefix = Properties.Settings.Default.SAMAccountPrefix;
            UserFilter = Properties.Settings.Default.UserFilter;
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
                    searcher.PropertiesToLoad.Add("managedBy");
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

                            string manager = null;
                            if (res.Properties.Contains("managedBy"))
                            {
                                manager = (string)res.Properties["managedBy"][0];
                            }

                            string managerUuid = null, managerUserId = null;
                            if (!string.IsNullOrEmpty(manager))
                            {
                                // do stuff
                                using (PrincipalContext ctx = new PrincipalContext(ContextType.Domain))
                                {
                                    using (UserPrincipal user = UserPrincipal.FindByIdentity(ctx, IdentityType.DistinguishedName, manager))
                                    {
                                        if (user != null)
                                        {
                                            managerUuid = user.Guid.ToString().ToLower();
                                            managerUserId = user.SamAccountName.ToLower();
                                        }
                                    }
                                }
                            }

                            ADOrgUnit ou = new ADOrgUnit();
                            ou.Uuid = uuid.ToString().ToLower();
                            ou.Name = name;
                            ou.Dn = dn;

                            if (parent?.Guid != null) {
	                            ou.ParentUUID = parent.Guid.ToString().ToLower();
                            }

                            if (managerUserId != null && managerUuid != null)
                            {
                                ou.Manager = new ADManager
                                {
                                    UserId = managerUserId,
                                    Uuid = managerUuid
                                };
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
                    if (Properties.Settings.Default.IncludeDisabledUsers)
                    {
                        searcher.Filter = CreateFilter("!(isDeleted=TRUE)");
                    }
                    else
                    {
                        searcher.Filter = CreateFilter("!(isDeleted=TRUE)", "!(UserAccountControl:1.2.840.113556.1.4.803:=2)");
                    }

                    searcher.PropertiesToLoad.Add(Properties.Settings.Default.UserTitleField);
                    searcher.PropertiesToLoad.Add("objectGUID");
                    searcher.PropertiesToLoad.Add(Properties.Settings.Default.UserNameField);
                    searcher.PropertiesToLoad.Add("distinguishedname");
                    searcher.PropertiesToLoad.Add("sAMAccountName");
                    searcher.PropertiesToLoad.Add("UserAccountControl");

                    if (!string.IsNullOrEmpty(Properties.Settings.Default.CustomUUIDField))
                    {
                        searcher.PropertiesToLoad.Add(Properties.Settings.Default.CustomUUIDField);
                    }

                    if (!string.IsNullOrEmpty(Properties.Settings.Default.NemLoginUUIDField))
                    {
                        searcher.PropertiesToLoad.Add(Properties.Settings.Default.NemLoginUUIDField);
                    }
                    else if (Properties.Settings.Default.ReadAltSecIdentities)
                    {
                        searcher.PropertiesToLoad.Add("altSecurityIdentities");
                    }

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
                            string uuid = null;
                            if (!string.IsNullOrEmpty(Properties.Settings.Default.CustomUUIDField))
                            {
                                if (res.Properties.Contains(Properties.Settings.Default.CustomUUIDField))
                                {
                                    uuid = (string)res.Properties[Properties.Settings.Default.CustomUUIDField][0];
                                }
                            }
                            else
                            {
                                Guid guid = new Guid((byte[])res.Properties["objectGUID"][0]);
                                uuid = guid.ToString().ToLower();
                            }

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

                            bool disabled = false;
                            const int AccountDisable = (int)0x0002;
                            int accountControlValue = (int)res.Properties["UserAccountControl"][0];
                            if ((accountControlValue & AccountDisable) == AccountDisable)
                            {
                                disabled = true;
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

                            string nemLoginUuid = null;
                            if (!string.IsNullOrEmpty(Properties.Settings.Default.NemLoginUUIDField))
                            {
                                if (res.Properties.Contains(Properties.Settings.Default.NemLoginUUIDField))
                                {
                                    nemLoginUuid = (string)res.Properties[Properties.Settings.Default.NemLoginUUIDField][0];
                                }
                            }
                            else if (Properties.Settings.Default.ReadAltSecIdentities)
                            {
                                try
                                {
                                    ResultPropertyValueCollection altSecIdentities = (ResultPropertyValueCollection)res.Properties["altSecurityIdentities"];

                                    for (int i = 0; i < altSecIdentities.Count; i++)
                                    {
                                        string val = (string)altSecIdentities[i];
                                        if (val == null)
                                        {
                                            continue;
                                        }

                                        if (val.StartsWith("NL3UUID-ACTIVE-NSIS"))
                                        {
                                            string[] tokens = val.Split('.');
                                            if (tokens.Length >= 3)
                                            {
                                                nemLoginUuid = tokens[2];
                                                break;
                                            }
                                        }
                                    }
                                }
                                catch (Exception ex)
                                {
                                    log.Warn("Failed to parse altSecurityIdentities for user " + userId, ex);
                                }
                            }

                            if (string.IsNullOrEmpty(uuid))
                            {
                                log.Warn("User " + userId + " did not have a uuid in attribute '" + Properties.Settings.Default.CustomUUIDField + "'");
                                continue;
                            }

                            try
                            {
                                new Guid(uuid);
                            }
                            catch (Exception)
                            {
                                log.Warn("User " + userId + " did not have a uuid in attribute '" + Properties.Settings.Default.CustomUUIDField + "'. Invalid value: " + uuid);
                                continue;
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
                            user.NemloginUuid = nemLoginUuid;
                            user.Disabled = disabled;

                            users.Add(user);
                        }
                    }
                }
            }

            return users;
        }

        private string CreateFilter(params string[] filters)
        {
            var allFilters = filters.ToList();
            allFilters.Add("objectClass=user");
            allFilters.Add("objectCategory=person");

            if (!string.IsNullOrEmpty(UserFilter))
            {
                allFilters.Add(UserFilter);
            }

            return string.Format("(&{0})", string.Concat(allFilters.Where(x => !String.IsNullOrEmpty(x)).Select(x => '(' + x + ')').ToArray()));
        }

    }
}

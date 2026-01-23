using System.Collections.Generic;
using System.Configuration;
using System.Linq;

namespace RoleCatalogImporter
{
    public static class SettingsHelper
    {
        public static List<string> AdditionalADOUs
        {
            get
            {
                var collection = Properties.Settings.Default.AdditionalADOU;
                if (collection != null && collection.Count > 0)
                    return collection.Cast<string>().ToList();

                // Bagudkompatibilitet: læs den rå værdi direkte fra app.config
                try
                {
                    var config = ConfigurationManager.OpenExeConfiguration(ConfigurationUserLevel.None);
                    var sectionGroup = config.SectionGroups["applicationSettings"];
                    if (sectionGroup != null)
                    {
                        var section = sectionGroup.Sections["RoleCatalogImporter.Properties.Settings"]
                            as ClientSettingsSection;

                        var setting = section?.Settings.Get("AdditionalADOU");
                        var xmlValue = setting?.Value?.ValueXml?.InnerText;

                        if (!string.IsNullOrWhiteSpace(xmlValue))
                        {
                            return new List<string> { xmlValue.Trim() };
                        }
                    }
                }
                catch
                {
                    /* Ignorer fejl hvis filen ikke findes eller formatet er anderledes */
                }

                // Sidste fallback
                return new List<string>();
            }
        }
    }
}
using System;
using System.Collections.Generic;

namespace RoleCatalogueAttributeStore
{
    public class Configuration
    {
        private static Configuration instance;

        public string RoleCatalogueUrl { get; set; }
        public string ApiKey { get; set; }
        public bool Debug { get; set; }
        public bool LogToEventLog { get; set; }
        public string Domain { get; set; }

        private Configuration()
        {
            ;
        }

        public void Init(Dictionary<string, string> config)
        {
            if (config.ContainsKey("RoleCatalogueUrl"))
            {
                RoleCatalogueUrl = config["RoleCatalogueUrl"];
            }
            else
            {
                throw new Exception("RoleCatalogueUrl not configured");
            }

            if (config.ContainsKey("ApiKey"))
            {
                ApiKey = config["ApiKey"];
            }
            else
            {
                throw new Exception("ApiKey not configured");
            }

            if (config.ContainsKey("Debug"))
            {
                Debug = "true".Equals(config["Debug"]);
            }
            else
            {
                Debug = false;
            }

            if (config.ContainsKey("LogToEventLog"))
            {
                LogToEventLog = "true".Equals(config["LogToEventLog"]);
            }
            else
            {
                LogToEventLog = false;
            }

            if (config.ContainsKey("Domain"))
            {
                Domain = config["Domain"];
            } else
            {
                Domain = null;
            }
        }

        public static Configuration GetInstance()
        {
            if (instance != null)
            {
                return instance;
            }

            return (instance = new Configuration());
        }

        public override string ToString()
        {
            return "Configuration: URL=" + RoleCatalogueUrl + ", Debug=" + Debug;
        }
    }
}

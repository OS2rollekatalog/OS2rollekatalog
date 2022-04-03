
using Newtonsoft.Json;
using RestSharp.Deserializers;
using System;
using System.Collections.Generic;

namespace RoleCatalogImporter
{
    class OrgUnit
    {
        public string uuid { get; set; }
        public string name { get; set; }
        public string parentOrgUnitUuid { get; set; }
        public Manager manager { get; set; }
    }
}

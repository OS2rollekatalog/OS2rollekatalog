using System.Collections.Generic;

namespace ADSyncService
{
    class ItSystemData
    {
        public long id { get; set; }
        public string identifier { get; set; }
        public string name { get; set; }
        public bool @readonly { get; set; }
        public List<SystemRole> systemRoles { get; set; }
        public bool convertRolesEnabled { get; set; }
    }

    class SystemRole
    {

        public string name { get; set; }
        public string identifier { get; set; }
        public string description { get; set; }
        public List<string> users { get; set; }
    }
}

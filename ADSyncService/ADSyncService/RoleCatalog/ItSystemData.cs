using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace ADSyncService
{
    class ItSystemData
    {
        public long id { get; set; }
        public string identifier { get; set; }
        public string name { get; set; }
        public List<SystemRole> systemRoles { get; set; }
        public bool convertRolesEnabled { get; set; }
    }

    class SystemRole
    {
        public string name { get; set; }
        public string identifier { get; set; }
        public string description { get; set; }
    }
}

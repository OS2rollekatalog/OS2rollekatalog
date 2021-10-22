using System.Collections.Generic;

namespace ADSyncService
{
    class Operation
    {
        public string systemRoleIdentifier { get; set; }
        public string itSystemIdentifier { get; set; }
        public bool active { get; set; }
        public string adGroupType { get; set; }
    }
}

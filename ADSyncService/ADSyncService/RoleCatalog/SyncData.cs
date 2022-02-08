using System.Collections.Generic;

namespace ADSyncService
{
    class SyncData
    {
        public long head { get; set; }
        public long maxHead { get; set; }
        public List<Assignment> assignments { get; set; }
    }
}

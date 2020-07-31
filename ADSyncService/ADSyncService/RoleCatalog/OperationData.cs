using System.Collections.Generic;

namespace ADSyncService
{
    class OperationData
    {
        public long head { get; set; }
        public List<Operation> operations { get; set; }
    }
}

using System.Collections.Generic;

namespace RoleCatalogImporter
{
    class User
    {
        public string extUuid { get; set; }
        public string userId { get; set; }
        public string name { get; set; }
        public string email { get; set; }
        public string cpr { get; set; }
        public string nemloginUuid { get; set; }
        public bool disabled { get; set; }
        public List<Position> positions { get; set; }
    }
}

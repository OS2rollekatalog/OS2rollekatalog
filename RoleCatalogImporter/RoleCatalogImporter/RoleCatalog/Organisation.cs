using System.Collections.Generic;

namespace RoleCatalogImporter
{
    class Organisation
    {
        public List<User> users { get; set; }
        public List<OrgUnit> orgUnits { get; set; }
    }
}

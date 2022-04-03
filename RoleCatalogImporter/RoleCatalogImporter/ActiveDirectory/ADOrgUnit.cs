
namespace RoleCatalogImporter
{
    class ADOrgUnit
    {
        public string Uuid { get; set; }
        public string Name { get; set; }
        public string Dn { get; set; }
        public string ParentUUID { get; set; }
        public ADManager Manager { get; set; }
    }
}

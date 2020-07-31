using System.Collections.Generic;

namespace RoleCatalogueAttributeStore
{
    public class UserResponse
    {
        public string oioBPP { get; set; }
        public string nameID { get; set; }
        public List<string> userRoles { get; set; }
        public List<string> systemRoles { get; set; }
        public List<string> dataRoles { get; set; }
        public List<string> functionRoles { get; set; }
        public Dictionary<string, string> roleMap { get; set; }
    }
}

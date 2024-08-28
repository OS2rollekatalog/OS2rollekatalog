using Newtonsoft.Json;

namespace RoleCatalogImporter
{
    public class CyberArk
    {
        [JsonProperty("Content")]
        public string Password { get; set; }
    }
}

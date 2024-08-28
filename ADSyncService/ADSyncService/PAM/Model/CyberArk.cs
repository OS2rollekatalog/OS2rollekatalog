using Newtonsoft.Json;

namespace ADSyncService
{
    public class CyberArk
    {
        [JsonProperty("Content")]
        public string Password { get; set; }
    }
}

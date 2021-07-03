
using System.Net;

namespace ADSyncService
{
    class Program
    {
        static void Main(string[] args)
        {
            ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;

            Configuration.Configure();
        }
    }
}

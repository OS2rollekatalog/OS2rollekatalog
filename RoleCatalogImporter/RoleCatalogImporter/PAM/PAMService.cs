using Newtonsoft.Json;
using System;
using System.Net.Http;
using System.Net.Http.Headers;

namespace RoleCatalogImporter
{
    class PAMService
    {
        private static readonly log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private static string cyberArkAppId = Properties.Settings.Default.CyberArkAppId;
        private static string cyberArkSafe = Properties.Settings.Default.CyberArkSafe;
        private static string cyberArkObject = Properties.Settings.Default.CyberArkObject;
        private static string cyberArkAPI = Properties.Settings.Default.CyberArkAPI;


        public static string GetApiKey()
        {
            string apiKey = null;
            HttpClient httpClient = GetHttpClient();
            var response = httpClient.GetAsync($"/AIMWebService/api/Accounts?AppID={cyberArkAppId}&Safe={cyberArkSafe}&Object={cyberArkObject}");
            response.Wait();
            response.Result.EnsureSuccessStatusCode();
            var responseString = response.Result.Content.ReadAsStringAsync();
            responseString.Wait();
            CyberArk cyberArk = JsonConvert.DeserializeObject<CyberArk>(responseString.Result);

            if (cyberArk != null && cyberArk.Password != null)
            {
                apiKey = cyberArk.Password;
            }

            return apiKey;
        }

        private static HttpClient GetHttpClient()
        {
            var httpClient = new HttpClient();
            httpClient.BaseAddress = new Uri(cyberArkAPI);
            httpClient.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
            return httpClient;
        }
    }
}



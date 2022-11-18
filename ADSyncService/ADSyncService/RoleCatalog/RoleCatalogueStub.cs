using System;
using RestSharp;
using System.Net;
using System.Collections.Generic;
using System.Configuration;

namespace ADSyncService
{
    class RoleCatalogueStub
    {
        private static readonly log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private static string apiKey = Properties.Settings.Default.ApiKey;
        private static string baseUrl;

        public RoleCatalogueStub()
        {
            ServicePointManager.ServerCertificateValidationCallback += (sender, cert, chain, error) => { return true; };

            baseUrl = Properties.Settings.Default.ApiUrl;

            if (baseUrl.EndsWith("/"))
            {
                baseUrl = baseUrl.Substring(0, baseUrl.Length - 1);
            }
        }

        public OperationData GetOperationData()
        {
            try
            {
                RestClient client = new RestClient(baseUrl);

                var request = new RestRequest("/api/ad/v2/operations", Method.GET);
                request.AddHeader("ApiKey", apiKey);
                request.JsonSerializer = NewtonsoftJsonSerializer.Default;

                var result = client.Execute<OperationData>(request);
                if (result.StatusCode.Equals(HttpStatusCode.OK))
                {
                    log.Debug("operation-data retrieved: " + result.Content);

                    return result.Data;
                }

                log.Error("OperationData call failed (" + result.StatusCode + ") : " + result.Content);
            }
            catch (Exception ex)
            {
                log.Error("OperationData call failed", ex);
            }

            // return empty result to ensure sync job does nothing
            return new OperationData()
            {
                head = 0,
                operations = new List<Operation>()
            };
        }

        public List<string> GetUsersInItSystem(string itSystemId)
        {
            RestClient client = new RestClient(baseUrl);

            var request = new RestRequest("/api/itsystem/" + itSystemId + "/users", Method.GET);
            request.AddHeader("ApiKey", apiKey);
            request.JsonSerializer = NewtonsoftJsonSerializer.Default;

            var result = client.Execute<List<string>>(request);
            if (result.StatusCode.Equals(HttpStatusCode.OK))
            {
                log.Debug("itsystem members retrieved: " + result.Content);

                List<string> newSamAccountNames = new List<string>();
                if (result.Data != null)
                {
                    foreach (var samaccountname in result.Data)
                    {
                        if (!newSamAccountNames.Contains(samaccountname.ToLower()))
                        {
                            newSamAccountNames.Add(samaccountname.ToLower());
                        }
                    }
                }

                return newSamAccountNames;
            }

            log.Error("Read ItSystem members call failed (" + result.StatusCode + ") : " + result.Content);
            return null;
        }

        public ItSystemData GetItSystemData(string itSystemId)
        {
            RestClient client = new RestClient(baseUrl);

            var request = new RestRequest("/api/itsystem/manage/" + itSystemId, Method.GET);
            request.AddHeader("ApiKey", apiKey);
            request.JsonSerializer = NewtonsoftJsonSerializer.Default;

            var result = client.Execute<ItSystemData>(request);
            if (result.StatusCode.Equals(HttpStatusCode.OK))
            {
                log.Debug("itsystem data retrieved: " + result.Content);

                return result.Data;
            }

            log.Error("Read ItSystem Data call failed (" + result.StatusCode + ") : " + result.Content);
            return null;
        }

        public void SetItSystemData(string itSystemId, ItSystemData itSystemData)
        {
            RestClient client = new RestClient(baseUrl);

            string updateUserAssignments = "";
            if (ReImportUsersEnabled())
            {
                updateUserAssignments = "?updateUserAssignments=true";
            }
            var request = new RestRequest("/api/itsystem/manage/" + itSystemId + updateUserAssignments, Method.POST);
            request.RequestFormat = DataFormat.Json;
            request.AddHeader("Content-Type", "application/json");
            request.AddHeader("ApiKey", apiKey);
            request.JsonSerializer = NewtonsoftJsonSerializer.Default;
            request.AddJsonBody(itSystemData);

            var result = client.Execute(request);
            if (result.StatusCode.Equals(HttpStatusCode.OK))
            {
                return;
            }

            log.Error("Set ItSystem Data call failed (" + result.StatusCode + ") : " + result.Content);
        }

        public SyncData GetSyncData()
        {
            try
            {
                RestClient client = new RestClient(baseUrl);

                var request = new RestRequest("/api/ad/v2/sync", Method.GET);
                request.AddHeader("ApiKey", apiKey);
                request.JsonSerializer = NewtonsoftJsonSerializer.Default;

                var result = client.Execute<SyncData>(request);
                if (result.StatusCode.Equals(HttpStatusCode.OK))
                {
                    log.Debug("sync-data retrieved: " + result.Content);

                    // lower-case the samaccountnames for easy comparison
                    var syncdata = result.Data;
                    foreach (var assignment in syncdata.assignments)
                    {
                        List<string> newSamAccountNames = new List<string>();

                        foreach (var samaccountname in assignment.samaccountNames)
                        {
                            if (!newSamAccountNames.Contains(samaccountname.ToLower()))
                            {
                                newSamAccountNames.Add(samaccountname.ToLower());
                            }
                        }

                        assignment.samaccountNames = newSamAccountNames;
                    }

                    return syncdata;
                }

                log.Error("Sync call failed (" + result.StatusCode + ") : " + result.Content);
            }
            catch (Exception ex)
            {
                log.Error("Sync call failed", ex);
            }

            // return empty result to ensure sync job does nothing
            return new SyncData()
            {
                head = 0,
                assignments = new List<Assignment>()
            };
        }

        public void ResetHead(long head, long maxHead)
        {
            try
            {
                RestClient client = new RestClient(baseUrl);

                var request = new RestRequest("/api/ad/v2/sync/" + head + "?maxHead=" + maxHead, Method.DELETE);
                request.AddHeader("ApiKey", apiKey);
                request.JsonSerializer = NewtonsoftJsonSerializer.Default;

                var result = client.Execute(request);
                if (result.StatusCode.Equals(HttpStatusCode.OK))
                {
                    log.Debug("reset head succeeded!");
                }
                else
                {
                    log.Error("Reset call failed (" + result.StatusCode + ") : " + result.Content);
                }
            }
            catch (Exception ex)
            {
                log.Error("Reset call failed", ex);
            }
        }

        public void ResetOperationHead(long head)
        {
            try
            {
                RestClient client = new RestClient(baseUrl);

                var request = new RestRequest("/api/ad/v2/operations/" + head, Method.DELETE);
                request.AddHeader("ApiKey", apiKey);
                request.JsonSerializer = NewtonsoftJsonSerializer.Default;

                var result = client.Execute(request);
                if (result.StatusCode.Equals(HttpStatusCode.OK))
                {
                    log.Debug("reset operation head succeeded!");
                }
                else
                {
                    log.Error("Reset operation call failed (" + result.StatusCode + ") : " + result.Content);
                }
            }
            catch (Exception ex)
            {
                log.Error("Reset operation call failed", ex);
            }
        }

        private static bool ReImportUsersEnabled()
        {
            string reImportUsers = ConfigurationManager.AppSettings["ReImportUsers"];
            return reImportUsers != null && reImportUsers.Equals("Yes");
        }
    }
}

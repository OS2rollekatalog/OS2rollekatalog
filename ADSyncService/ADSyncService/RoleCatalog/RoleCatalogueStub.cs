using System;
using RestSharp;
using System.Net;
using System.Collections.Generic;
using System.Security.Cryptography.X509Certificates;

namespace ADSyncService
{
    class RoleCatalogueStub
    {
        private static readonly log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
        private static string apiKey = Properties.Settings.Default.ApiKey;
        private static string baseUrl;

        public RoleCatalogueStub()
        {
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
                            newSamAccountNames.Add(samaccountname.ToLower());
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

        public void ResetHead(long head)
        {
            try
            {
                RestClient client = new RestClient(baseUrl);

                var request = new RestRequest("/api/ad/v2/sync/" + head, Method.DELETE);
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

    }
}

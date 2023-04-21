using System;
using System.Collections.Generic;
using System.IdentityModel;
using Microsoft.IdentityServer.ClaimsPolicy.Engine.AttributeStore;

namespace RoleCatalogueAttributeStore
{
    public class MainClass : IAttributeStore
    {
        public IAsyncResult BeginExecuteQuery(string query, string[] parameters, AsyncCallback callback, object state)
        {
            ValidateQuery(query, parameters);

            string[][] outputValues = new string[1][];
            switch (query)
            {
                // TODO: create a much better query lookup language, that can return multiple values in one call
                case "getNameID":
                case "nameid": // legacy command
                    AttributeStoreLogger.Debug("Performing NameID lookup on user '" + parameters[0] + "'");

                    outputValues[0] = new string[1] { RoleCatalogueStub.GetNameID(parameters[0]) };

                    AttributeStoreLogger.Debug("NameID query result is '" + outputValues[0][0] + "'");
                    break;
                case "getNemLoginRoles":
                    AttributeStoreLogger.Debug("Performing NemLog-in OIO-BPP lookup on user '" + parameters[0] + "'");

                    outputValues[0] = new string[1] { RoleCatalogueStub.GetNemLoginOIOBPP(parameters[0]) };

                    AttributeStoreLogger.Debug("NemLog-in OIO-BPP query result is '" + outputValues[0][0] + "'");
                    break;
                case "getBasicPriviligeProfile":
                case "oio-bpp": // legacy command
                    AttributeStoreLogger.Debug("Performing OIO-BPP lookup on user '" + parameters[0] + "' for it-system '" + parameters[1] + "'");

                    outputValues[0] = new string[1] { RoleCatalogueStub.GetOIOBPP(parameters[0], parameters[1]) };

                    AttributeStoreLogger.Debug("OIO-BPP query result is '" + outputValues[0][0] + "'");
                    break;
                case "getSystemRoles":
                case "systemroles": // legacy command
                    AttributeStoreLogger.Debug("Performing List systemroles lookup on user '" + parameters[0] + "' for it-system '" + parameters[1] + "'");

                    outputValues = RoleCatalogueStub.GetSystemRoles(parameters[0], parameters[1]);

                    AttributeStoreLogger.Debug("List systemroles query result is '" + outputValues.Length + "' roles");
                    break;
                case "getUserRoles":
                case "userroles": // legacy command
                    AttributeStoreLogger.Debug("Performing List userroles lookup on user '" + parameters[0] + "' for it-system '" + parameters[1] + "'");

                    outputValues = RoleCatalogueStub.GetUserRoles(parameters[0], parameters[1]);

                    AttributeStoreLogger.Debug("List userroles query result is '" + outputValues.Length + "' roles");
                    break;
                case "getDataRoles":
                    AttributeStoreLogger.Debug("Performing List dataroles lookup on user '" + parameters[0] + "' for it-system '" + parameters[1] + "'");

                    outputValues = RoleCatalogueStub.GetDataRoles(parameters[0], parameters[1]);

                    AttributeStoreLogger.Debug("List dataroles query result is '" + outputValues.Length + "' roles");
                    break;
                case "getFunctionRoles":
                    AttributeStoreLogger.Debug("Performing List functionroles lookup on user '" + parameters[0] + "' for it-system '" + parameters[1] + "'");

                    outputValues = RoleCatalogueStub.GetFunctionRoles(parameters[0], parameters[1]);

                    AttributeStoreLogger.Debug("List functionroles query result is '" + outputValues.Length + "' roles");
                    break;
                case "hasUserRole":
                    AttributeStoreLogger.Debug("Performing single userrole lookup on user '" + parameters[0] + "' and roleId '" + parameters[1] + "' for it-system '" + parameters[2] + "'");

                    outputValues = RoleCatalogueStub.VerifySingleUserRole(parameters[0], parameters[1], parameters[2]);

                    AttributeStoreLogger.Debug("Single userrole query result is '" + outputValues.Length + "' roles");
                    break;
                case "hasSystemRole":
                    AttributeStoreLogger.Debug("Performing single systemrole lookup on user '" + parameters[0] + "' and roleId '" + parameters[1] + "' for it-system '" + parameters[2] + "'");

                    outputValues = RoleCatalogueStub.VerifySingleSystemRole(parameters[0], parameters[1], parameters[2]);

                    AttributeStoreLogger.Debug("Single systemrole query result is '" + outputValues.Length + "' roles");
                    break;
                default:
                    throw new AttributeStoreQueryFormatException("The query string is not supported:" + query);
            }

            TypedAsyncResult<string[][]> asyncResult = new TypedAsyncResult<string[][]>(callback, state);
            asyncResult.Complete(outputValues, true);

            return asyncResult;
        }

        public string[][] EndExecuteQuery(IAsyncResult result)
        {
            return TypedAsyncResult<string[][]>.End(result);
        }

        public void Initialize(Dictionary<string, string> config)
        {
            try
            {
                Configuration.GetInstance().Init(config);
                AttributeStoreLogger.Init();
                AttributeStoreLogger.Info("RoleCatalogue loaded. details :" + Configuration.GetInstance().ToString());
            }
            catch (Exception ex)
            {
                throw new AttributeStoreInvalidConfigurationException("Failed to load configuration: " + ex.Message + ". Details: " + ex.StackTrace.ToString());
            }
        }

        private void ValidateQuery(string query, string[] parameters)
        {
            if (string.IsNullOrEmpty(query))
            {
                throw new AttributeStoreQueryFormatException("No query string.");
            }

            if (parameters == null)
            {
                throw new AttributeStoreQueryFormatException("No query parameter.");
            }

            if ("getNameID".Equals(query) && parameters.Length != 1)
            {
                throw new AttributeStoreQueryFormatException("One query parameter required for nameid (user-id)");
            }

            if (("getDataRoles".Equals(query) || "getFunctionRoles".Equals(query) || "getBasicPriviligeProfile".Equals(query) || "getSystemRoles".Equals(query) || "getUserRoles".Equals(query)) && parameters.Length != 2)
            {
                throw new AttributeStoreQueryFormatException("Two query parameters required for role lookup (user-id and it-system)");
            }

            if (("hasUserRole".Equals(query) || "hasSystemRole".Equals(query)) && parameters.Length != 3)
            {
                throw new AttributeStoreQueryFormatException("Three query parameters required for single role lookup (userid, role-id and it-system)");
            }
        }
    }
}

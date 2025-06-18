using log4net.Appender;
using log4net.Repository.Hierarchy;
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace ADSyncService.Util
{
    internal class AccessChecker
    {
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);

        public static void ValidateWriteAccess(String path)
        {
            string testfil = Path.Combine(path, "skrivetest.tmp");
            try
            {
                log.Info("Ensuring we got write access in " + path);
                // Forsøg at oprette og slette en midlertidig fil
                File.WriteAllText(testfil, "test");
                File.Delete(testfil);
                log.Debug("Done");
            }
            catch (UnauthorizedAccessException e)
            {
                log.Error("Der mangler skrive adgang til " + path + " folderen", e);
            }
            catch (Exception e)
            {
                log.Error("Kan ikke sprive til " + path + " folderen", e);
            }
        }
    }
}

using ADSyncService.Util;
using System;
using System.IO;
using System.Reflection;
using System.Threading;
using Microsoft.Data.Sqlite;

namespace ADSyncService.Persistance
{
    public class SqliteConnectionManager
    {
        private static readonly object syncLock = new object();
        private static log4net.ILog log = log4net.LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);

        private static SqliteConnectionManager _instance;
        private static readonly object _initLock = new object();
        private readonly string _connectionString;

        private SqliteConnectionManager()
        {
            var programFolder = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location);
            AccessChecker.ValidateWriteAccess(programFolder);
            
            var persistenceFolder = Path.Combine(programFolder, "persistence");
            lock (syncLock)
            {
                Directory.CreateDirectory(persistenceFolder);
            }
            var persistencePath = Path.Combine(persistenceFolder, "persistence.db");
            
            log.Debug("Directory ensured at {DateTime.Now}");


            _connectionString = $"Data Source={persistencePath};Cache=Shared;Mode=ReadWriteCreate;Default Timeout=5;";
            log.Info("SqliteConnectionManager initialized with path: {persistencePath}");
        }

        public static void Initialize()
        {
            lock (_initLock)
            {
                if (_instance == null)
                {
                    _instance = new SqliteConnectionManager();
                }
                else
                {
                    log.Debug("SqliteConnectionManager is already initialized - ignoring.");
                }
            }
        }

        public static SqliteConnectionManager Instance
        {
            get
            {
                if (_instance == null)
                    throw new InvalidOperationException("SqliteConnectionManager has not been initialized.");
                return _instance;
            }
        }

        public SqliteConnection GetOpenConnection()
        {
            var connection = new SqliteConnection(_connectionString);
            connection.Open();
            return connection;
        }

    }
}

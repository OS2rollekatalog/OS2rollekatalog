using Topshelf;

namespace RoleCatalogImporter
{
    internal static class Configuration
    {
        internal static void Configure()
        {
            HostFactory.Run(configure =>
            {
                configure.Service<Application>(service =>
                {
                    service.ConstructUsing(s => new Application());
                    service.WhenStarted(s => s.Start());
                    service.WhenStopped(s => s.Stop());
                });

                configure.RunAsLocalService();
                configure.SetServiceName("OS2RollekatalogImporter");
                configure.SetDisplayName("OS2Rollekatalog Importer");
                configure.SetDescription("Henter organisationsdata fra AD og sender til OS2rollekatalog");
            });
        }
    }
}

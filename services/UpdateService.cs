using System.Threading.Tasks;

namespace ECF_Notificador.services
{
    public class UpdateService
    {
        private readonly SettingsService _settings;

        public UpdateService(SettingsService settings)
        {
            _settings = settings;
        }

        public async Task CheckForUpdates()
        {
            var versionService = new EcfVersionService();
            var latestService = new EcfLatestVersionService();
            
            // CORREÇÃO: Passando apenas 1 argumento (DownloadUrl)
            var notification = new NotificationService(_settings.DownloadUrl);

            var local = versionService.GetInstalledVersion(_settings.EcfInstallPath);
            var latest = await latestService.GetLatestVersion(_settings.DownloadUrl, _settings.VersionHtmlClass);

            if (local != null && latest != null && local != latest)
            {
                notification.ShowUpdate(local, latest);
            }
        }
    }
}
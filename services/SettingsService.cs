using System.Text.Json;
using System.IO;
using System;

namespace ECF_Notificador.services
{
    public class SettingsService
    {
        private readonly string _settingsPath;
        private AppConfig? _appConfig;

        public SettingsService()
        {
            var configFolder = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "config");
            _settingsPath = Path.Combine(configFolder, "appSettings.json");
            LoadSettings();
        }

        public int CheckIntervalHours
        {
            get => _appConfig?.Settings?.CheckIntervalHours ?? 6;
            set
            {
                if (_appConfig?.Settings != null)
                {
                    _appConfig.Settings.CheckIntervalHours = value;
                    SaveSettings();
                }
            }
        }

        // Propriedades para acessar os dados do ECF
        public string EcfInstallPath => _appConfig?.Ecf?.InstallPath ?? "";
        public string DownloadUrl => _appConfig?.Ecf?.DownloadUrl ?? "";
        public string VersionHtmlClass => _appConfig?.Ecf?.VersionHtmlClass ?? "rfb_subheader";

        // Nova propriedade para centralizar o caminho do ícone
        public string IconPath => _appConfig?.Settings?.IconPath ?? "app_icon.ico";

        private void LoadSettings()
        {
            try
            {
                if (File.Exists(_settingsPath))
                {
                    var json = File.ReadAllText(_settingsPath);
                    _appConfig = JsonSerializer.Deserialize<AppConfig>(json);
                }
            }
            catch { }

            _appConfig ??= new AppConfig();
        }

        private void SaveSettings()
        {
            try
            {
                // Garante que o diretório 'config' existe antes de salvar
                var directory = Path.GetDirectoryName(_settingsPath);
                if (!string.IsNullOrEmpty(directory) && !Directory.Exists(directory))
                {
                    Directory.CreateDirectory(directory);
                }

                var json = JsonSerializer.Serialize(_appConfig, new JsonSerializerOptions { WriteIndented = true });
                File.WriteAllText(_settingsPath, json);
            }
            catch (Exception ex)
            {
                Console.WriteLine("Erro ao salvar config: " + ex.Message);
            }
        }

        private class AppConfig
        {
            public EcfSettings Ecf { get; set; } = new();
            public SettingsData Settings { get; set; } = new();
        }

        private class EcfSettings
        {
            public string InstallPath { get; set; } = "";
            public string DownloadUrl { get; set; } = "";
            public string VersionHtmlClass { get; set; } = "rfb_subheader";
        }

        private class SettingsData
        {
            public int CheckIntervalHours { get; set; } = 6;
            // Mude para o nome correto do seu arquivo
            public string IconPath { get; set; } = "EcfNotificador.ico";
        }
    }
}
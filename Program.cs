using System;
using System.Drawing;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;
using ECF_Notificador.services;
using Microsoft.Toolkit.Uwp.Notifications; // Necessário para o OnActivated

namespace ECF_Notificador
{
    class Program
    {
        private static SettingsService? _settings;
        private static NotifyIcon? _notifyIcon;
        private static CancellationTokenSource? _cts;
        private static Task? _backgroundTask;

        [STAThread]
        static void Main()
        {
            // Ajuste CRÍTICO: Registra o App ID para o Windows mostrar o ícone do EXE no topo do Toast
            ToastNotificationManagerCompat.OnActivated += toastArgs => { };

            ApplicationConfiguration.Initialize();

            _settings = new SettingsService();

            _notifyIcon = new NotifyIcon()
            {
                // Tenta extrair o ícone do próprio executável (mais seguro)
                Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath) ?? SystemIcons.Information,
                Visible = true,
                Text = "ECF Notificador"
            };

            var menu = new ContextMenuStrip();
            menu.Items.Add("Verificar agora", null, async (s, e) => await Check());
            menu.Items.Add("Configurações", null, (s, e) => ShowConfigForm());
            menu.Items.Add("-");
            menu.Items.Add("Sair", null, (s, e) =>
            {
                _cts?.Cancel();
                if (_notifyIcon != null)
                    _notifyIcon.Visible = false;
                Application.Exit();
            });

            _notifyIcon.ContextMenuStrip = menu;

            _cts = new CancellationTokenSource();
            _backgroundTask = RunBackgroundLoop(_cts.Token);

            Application.Run();
        }

        static async Task RunBackgroundLoop(CancellationToken token)
        {
            while (!token.IsCancellationRequested)
            {
                await Check();
                var interval = TimeSpan.FromHours(_settings?.CheckIntervalHours ?? 6);
                await Task.Delay(interval, token);
            }
        }

        static async Task Check()
        {
            try
            {
                if (_settings != null)
                {
                    var updateService = new UpdateService(_settings);
                    await updateService.CheckForUpdates();
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[{DateTime.Now}] Erro: {ex.Message}");
            }
        }

        static void ShowConfigForm()
        {
            if (_settings != null)
            {
                var form = new ConfigForm(_settings);
                form.ShowDialog();
            }
        }
    }
}
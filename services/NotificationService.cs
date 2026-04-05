using Microsoft.Toolkit.Uwp.Notifications;
using System;

namespace ECF_Notificador.services
{
    public class NotificationService
    {
        private readonly string _downloadUrl;

        public NotificationService(string downloadUrl)
        {
            _downloadUrl = downloadUrl;
        }

        public void ShowUpdate(string current, string latest)
        {
            new ToastContentBuilder()
                .AddArgument("action", "viewUpdate")
                // O Header ajuda a destacar a informação no topo, perto do ícone do app
                .AddHeader("6221", "🚀 Atualização Disponível", "")
                .AddText("ECF - Escrituração Contábil Fiscal")
                .AddText($"Nova versão: {latest}")
                .AddText($"Versão instalada: {current}")

                // Mantém a notificação aberta até o usuário interagir
                .SetToastScenario(ToastScenario.Reminder)

                .AddButton(new ToastButton()
                    .SetContent("Baixar agora")
                    .SetProtocolActivation(new Uri(_downloadUrl)))

                .AddButton(new ToastButton()
                    .SetContent("Fechar")
                    .SetDismissActivation())
                
                .Show();
        }
    }
}
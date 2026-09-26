package app;

import app.form.ConfigForm;
import app.form.NativeConfigDialog;
import app.service.SettingsService;
import app.service.TrayIconService;
import app.service.UpdateScheduler;
import app.service.UpdateService;
import app.service.win32.NativeTrayService;
import app.utils.ThemeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ponto de entrada da aplicacao.
 *
 * <p>Roda em segundo plano com um icone na bandeja do Windows e verifica, em
 * intervalos configuraveis, se ha uma nova versao do programa ECF publicada
 * pela Receita Federal.</p>
 */
public final class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    private App() {
    }

    public static void main(String[] args) {
        boolean nativeUi = isNativeImage();

        if (!nativeUi) {
            System.setProperty("java.awt.headless", "false");

            // Aplica o tema nativo antes de criar qualquer componente Swing.
            ThemeService.install();
        }

        SettingsService settings = new SettingsService();

        // Fila unica para todas as verificacoes: menu, duplo clique, agendador e
        // a verificacao inicial. Ver UpdateScheduler para o motivo.
        UpdateScheduler scheduler = new UpdateScheduler();
        UpdateScheduler.CheckTask verificacao = () -> {
            // Renova a configuracao antes de checar: assim "Verificar agora"
            // tambem propaga imediatamente uma mudanca feita no Gist, e a
            // verificacao ja usa a URL e a classe HTML mais recentes.
            settings.refreshFromGist();

            UpdateService updateService = new UpdateService(settings);
            return updateService.checkForUpdates();
        };

        if (nativeUi) {
            // Em binario nativo o AWT/Swing nao funciona: a bandeja e a tela de
            // configuracoes usam Win32 via JNA.
            NativeTrayService tray = new NativeTrayService(settings);
            tray.addCheckNowAction(() -> scheduler.submit(verificacao));
            tray.addDoubleClickAction(() -> scheduler.submit(verificacao));
            tray.addSettingsAction(() -> new NativeConfigDialog(settings).show());
            tray.addExitAction(() -> {
                scheduler.shutdown();
                tray.remove();
                System.exit(0);
            });
            tray.show();
        } else {
            TrayIconService tray = new TrayIconService(settings);
            tray.addCheckNowAction(() -> scheduler.submit(verificacao));
            tray.addDoubleClickAction(() -> scheduler.submit(verificacao));
            tray.addSettingsAction(() -> new ConfigForm(settings).setVisible(true));
            tray.addExitAction(() -> {
                scheduler.shutdown();
                tray.remove();
                System.exit(0);
            });
            tray.show();
        }

        scheduler.scheduleAtFixedRate(verificacao, settings.getCheckIntervalHours());
        scheduler.scheduleGistRefresh(settings::refreshFromGist);

        // Verificacao inicial, pelo mesmo caminho do menu e do duplo clique.
        scheduler.submit(verificacao);
    }

    /**
     * Detecta se o programa roda como binario nativo do GraalVM. Nesse modo o
     * AWT/Swing nao tem suporte, entao a UI usa Win32 via JNA.
     */
    private static boolean isNativeImage() {
        return System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    }
}

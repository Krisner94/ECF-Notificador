package app.service;

import app.utils.Message;
import app.utils.TextSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.awt.Desktop;
import java.net.URI;

/**
 * Exibe o aviso de atualizacao para o usuario.
 *
 * <p>O conteudo exibido vem do site da Receita Federal e passa por
 * {@link TextSanitizer} antes de chegar a API nativa: o popup e uma janela do
 * sistema operacional e nao deve receber texto de controle ou de tamanho
 * ilimitado vindo da rede.</p>
 *
 * <p><b>Threading:</b> o popup e modal. Se chamado da Event Dispatch Thread
 * (itens do menu da bandeja rodam na EDT), a interface congelaria; por isso o
 * fluxo e desviado para uma thread propria. Todos os caminhos - menu, duplo
 * clique e agendador - terminam no mesmo popup.</p>
 */
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final int MB_OK = 0x00000000;
    private static final int MB_ICONINFORMATION = 0x00000040;
    private static final int MB_SETFOREGROUND = 0x00010000;
    private static final int MB_TOPMOST = 0x00040000;

    private final String downloadUrl;
    private final String iconPath;

    public NotificationService(String downloadUrl, String iconPath) {
        this.downloadUrl = downloadUrl;
        this.iconPath = iconPath;
    }

    public void showUpdate(String current, String latest) {
        // O popup e modal: chamado da EDT (itens de menu da bandeja), congelaria
        // a interface. Desviar para uma thread propria mantem o mesmo caminho
        // para menu, duplo clique e agendador.
        if (SwingUtilities.isEventDispatchThread()) {
            Thread worker = new Thread(() -> runUpdateFlow(current, latest), "ecf-notificacao");
            worker.setDaemon(true);
            worker.start();
            return;
        }
        runUpdateFlow(current, latest);
    }

    /**
     * Monta a instrucao principal do popup.
     *
     * @return o texto pronto, ou {@code null} se a versao nao for valida.
     */
    static String buildInstruction(String latest) {
        String versaoSegura = TextSanitizer.sanitizeVersion(latest);
        if (versaoSegura == null) {
            return null;
        }
        return "Nova vers\u00e3o " + versaoSegura + " dispon\u00edvel do ECF";
    }

    /** @return a linha com a versao instalada, ou {@code null} se invalida. */
    static String buildContent(String current) {
        String versaoSegura = TextSanitizer.sanitizeVersion(current);
        if (versaoSegura == null) {
            return null;
        }
        return "Vers\u00e3o atual: " + versaoSegura;
    }

    /**
     * Sem URL HTTP/HTTPS valida o link nao e exibido: o usuario recebe uma
     * orientacao generica em vez de um destino invalido.
     */
    static String buildDetails(String url) {
        String urlSegura = TextSanitizer.sanitizeUrlOrNull(url);
        if (urlSegura == null) {
            return "Consulte a p\u00e1gina oficial de download da Receita Federal.";
        }
        return "P\u00e1gina oficial de download:" + System.lineSeparator() + urlSegura;
    }

    private void runUpdateFlow(String current, String latest) {
        String title = Message.NOTIFICATION_UPDATE_TITLE.getValue();
        String instruction = buildInstruction(latest);
        String content = buildContent(current);
        String urlSegura = TextSanitizer.sanitizeUrlOrNull(downloadUrl);
        String details = buildDetails(downloadUrl);

        if (instruction == null || content == null) {
            log.warn("Aviso ignorado: versoes invalidas (atual='{}', nova='{}').", current, latest);
            return;
        }

        // Popup nativo: funciona mesmo com as notificacoes do Windows
        // desligadas, cenario em que o balao do TrayIcon e descartado.
        if (isWindows()) {
            TaskDialogService.Outcome outcome =
                    TaskDialogService.showUpdateDialog(title, instruction, content, details, iconPath);

            if (outcome == TaskDialogService.Outcome.SHOWN_ACCEPTED) {
                openDownloadPage(urlSegura);
                return;
            }
            if (outcome == TaskDialogService.Outcome.SHOWN_DECLINED) {
                log.info("Usuario optou por adiar a atualizacao.");
                return;
            }
            log.warn("Popup nativo indisponivel. Usando caixa de mensagem do sistema.");
        }

        // Ultimo recurso: caixa de mensagem apenas informativa, sem perguntar nada.
        String fallbackText = instruction + System.lineSeparator()
                + System.lineSeparator() + content + System.lineSeparator()
                + System.lineSeparator() + details;
        int flags = MB_OK | MB_ICONINFORMATION | MB_SETFOREGROUND | MB_TOPMOST;
        WinApiService.showMessageBox(title, fallbackText, flags);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private void openDownloadPage(String urlSegura) {
        if (urlSegura == null) {
            log.warn("Pagina de download nao aberta: URL invalida.");
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI(urlSegura));
        } catch (Exception e) {
            log.error("Erro ao abrir a pagina de download: {}", e.getMessage());
        }
    }
}

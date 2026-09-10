package app.service;

import app.utils.TextSanitizer;
import app.utils.VersionComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Verifica atualizacoes: le a versao instalada, consulta a publicada e, quando
 * ha novidade real, aciona a notificacao.
 *
 * <p>As dependencias externas sao injetadas para permitir testar a decisao sem
 * tocar em disco, rede ou janela.</p>
 */
public class UpdateService {

    private static final Logger log = LoggerFactory.getLogger(UpdateService.class);

    public enum CheckResult {
        /** Ha versao mais nova publicada; o usuario foi avisado. */
        UPDATE_AVAILABLE,
        /** A instalada ja e a mais recente (ou e mais nova que a publicada). */
        UP_TO_DATE,
        /** Nao foi possivel comparar (arquivo ausente, site fora do ar, etc.). */
        UNKNOWN
    }

    /** Aciona o aviso; interface para o teste observar sem abrir janela. */
    @FunctionalInterface
    public interface UpdateNotifier {
        /**
         * @param currentVersion versao instalada (ja sanitizada).
         * @param latestVersion  versao publicada (ja sanitizada).
         */
        void notifyUpdate(String currentVersion, String latestVersion);
    }

    private final SettingsService settings;
    private final EcfVersionService versionService;
    private final EcfLatestVersionService latestVersionService;
    private final UpdateNotifier notifier;
    private final UpdateHistoryStore history;

    public UpdateService(SettingsService settings) {
        this(settings, new EcfVersionService(), new EcfLatestVersionService(),
                UpdateHistoryStore.defaultStore());
    }

    public UpdateService(SettingsService settings,
                         EcfVersionService versionService,
                         EcfLatestVersionService latestVersionService) {
        this(settings, versionService, latestVersionService, UpdateHistoryStore.defaultStore());
    }

    public UpdateService(SettingsService settings,
                         EcfVersionService versionService,
                         EcfLatestVersionService latestVersionService,
                         UpdateHistoryStore history) {
        this(settings, versionService, latestVersionService,
                (current, latest) -> new NotificationService(
                        settings.getDownloadUrl(), settings.getIconPath())
                        .showUpdate(current, latest),
                history);
    }

    public UpdateService(SettingsService settings,
                         EcfVersionService versionService,
                         EcfLatestVersionService latestVersionService,
                         UpdateNotifier notifier) {
        this(settings, versionService, latestVersionService, notifier, null);
    }

    public UpdateService(SettingsService settings,
                         EcfVersionService versionService,
                         EcfLatestVersionService latestVersionService,
                         UpdateNotifier notifier,
                         UpdateHistoryStore history) {
        this.settings = settings;
        this.versionService = versionService;
        this.latestVersionService = latestVersionService;
        this.notifier = notifier;
        this.history = history;
    }

    /** Executa a verificacao, grava o historico e informa o que aconteceu. */
    public CheckResult checkForUpdates() {
        CheckResult resultado = executarVerificacao();
        registrarHistorico(resultado);
        return resultado;
    }

    /** Decide o resultado sem gravar historico. */
    public CheckResult executarVerificacao() {
        // A versao instalada vem de arquivo local, mas tambem e sanitizada: se o
        // arquivo for editado a mao, o texto estranho nao chega ao popup nativo.
        String local = TextSanitizer.sanitizeVersion(
                versionService.getInstalledVersion(settings.getEcfInstallPath()));
        String latest = TextSanitizer.sanitizeVersion(
                latestVersionService.getLatestVersion(
                        settings.getDownloadUrl(), settings.getVersionHtmlClass()));

        if (local == null || latest == null) {
            log.info("Nao foi possivel comparar versoes (local='{}', ultima='{}').", local, latest);
            return CheckResult.UNKNOWN;
        }

        if (!VersionComparator.isNewer(latest, local)) {
            log.info("Nenhuma atualizacao disponivel. Versao instalada '{}' "
                    + "ja e a mais recente (publicada: '{}').", local, latest);
            return CheckResult.UP_TO_DATE;
        }

        log.info("Atualizacao disponivel: '{}' -> '{}'.", local, latest);

        // Sem URL segura o popup levaria o usuario a um destino invalido.
        Optional<String> urlSegura = Optional.ofNullable(
                TextSanitizer.sanitizeUrlOrNull(settings.getDownloadUrl()));
        if (urlSegura.isEmpty()) {
            log.warn("Atualizacao '{}' encontrada, mas a URL de download '{}' nao e "
                    + "HTTP/HTTPS valida. Nenhum aviso sera exibido.",
                    latest, settings.getDownloadUrl());
            return CheckResult.UNKNOWN;
        }

        notifier.notifyUpdate(local, latest);
        return CheckResult.UPDATE_AVAILABLE;
    }

    private void registrarHistorico(CheckResult resultado) {
        if (history == null) {
            return;
        }
        history.record(
                versionService.getInstalledVersion(settings.getEcfInstallPath()),
                latestVersionService.getLatestVersion(
                        settings.getDownloadUrl(), settings.getVersionHtmlClass()),
                resultado);
    }
}

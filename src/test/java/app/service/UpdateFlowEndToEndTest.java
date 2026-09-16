package app.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fluxo ponta a ponta sem internet, UI ou APIs nativas do Windows.
 *
 * <p>Exercita a mesma cadeia usada pela aplicacao: Gist HTTP, cache local,
 * pagina da Receita, arquivo da instalacao, decisao de atualizacao e historico.
 * WireMock representa os dois servidores externos e {@code @TempDir} isola os
 * arquivos produzidos pelo fluxo.</p>
 */
@Tag("servidor")
class UpdateFlowEndToEndTest {

    private static final String GIST_PATH = "/config/appSettings.json";
    private static final String PAGE_PATH = "/ecf.html";

    private static WireMockServer server;
    private static String gistUrl;
    private static String pageUrl;

    @BeforeAll
    static void startServer() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        String baseUrl = "http://localhost:" + server.port();
        gistUrl = baseUrl + GIST_PATH;
        pageUrl = baseUrl + PAGE_PATH;
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @BeforeEach
    void resetServer() {
        server.resetAll();
    }

    @Test
    @DisplayName("percorre Gist, Receita, instalacao, notificacao e historico")
    void executaFluxoCompleto(@TempDir Path tempDir) throws Exception {
        Path installFile = tempDir.resolve("response.varfile");
        Files.writeString(installFile, "sys.version=12.2.5\n", StandardCharsets.UTF_8);
        Path settingsFile = tempDir.resolve("config").resolve("appSettings.json");
        Path historyFile = tempDir.resolve("config").resolve("updateHistory.log");

        server.stubFor(get(urlPathEqualTo(GIST_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("ETag", "\"flow-1\"")
                        .withHeader("Content-Type", "application/json")
                        .withBody(gistJson(installFile))));
        server.stubFor(get(urlPathEqualTo(PAGE_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><div class=\"rfb_subheader\">Versao 12.2.6</div></body></html>")));

        SettingsService settings = new SettingsService(
                new GistSettingsLoader(gistUrl), settingsFile);
        UpdateHistoryStore history = new UpdateHistoryStore(historyFile);
        CapturedNotification notification = new CapturedNotification();
        UpdateService service = new UpdateService(
                settings,
                new EcfVersionService(),
                new EcfLatestVersionService(),
                notification,
                history);

        assertThat(settings.getDownloadUrl()).isEqualTo(pageUrl);
        assertThat(service.checkForUpdates()).isEqualTo(UpdateService.CheckResult.UPDATE_AVAILABLE);
        assertThat(notification.installedVersion).isEqualTo("12.2.5");
        assertThat(notification.latestVersion).isEqualTo("12.2.6");
        assertThat(history.size()).isEqualTo(1);
        assertThat(history.lastEntry().result()).isEqualTo(UpdateService.CheckResult.UPDATE_AVAILABLE);
        assertThat(Files.readString(settingsFile)).contains(pageUrl);
    }

    @Test
    @DisplayName("preserva o cache quando o Gist falha e nao notifica sem pagina valida")
    void preservaCacheDuranteFalha(@TempDir Path tempDir) throws Exception {
        Path installFile = tempDir.resolve("response.varfile");
        Files.writeString(installFile, "sys.version=12.2.5\n", StandardCharsets.UTF_8);
        Path settingsFile = tempDir.resolve("config").resolve("appSettings.json");
        Path historyFile = tempDir.resolve("config").resolve("updateHistory.log");

        server.stubFor(get(urlPathEqualTo(GIST_PATH))
                .willReturn(aResponse().withStatus(200).withBody(gistJson(installFile))));
        server.stubFor(get(urlPathEqualTo(PAGE_PATH))
                .willReturn(aResponse().withStatus(500)));

        SettingsService online = new SettingsService(
                new GistSettingsLoader(gistUrl), settingsFile);
        CapturedNotification firstNotification = new CapturedNotification();
        UpdateService firstCheck = new UpdateService(
                online, new EcfVersionService(), new EcfLatestVersionService(),
                firstNotification, new UpdateHistoryStore(historyFile));
        assertThat(firstCheck.checkForUpdates()).isEqualTo(UpdateService.CheckResult.UNKNOWN);
        assertThat(firstNotification.installedVersion).isNull();

        server.resetAll();
        server.stubFor(get(urlPathEqualTo(GIST_PATH)).willReturn(aResponse().withStatus(503)));
        SettingsService offline = new SettingsService(
                new GistSettingsLoader(gistUrl), settingsFile);
        assertThat(offline.getDownloadUrl()).isEqualTo(pageUrl);
    }

    private String gistJson(Path installFile) {
        return """
                {
                  "Ecf": {
                    "InstallPath": "%s",
                    "DownloadUrl": "%s",
                    "VersionHtmlClass": "rfb_subheader"
                  },
                  "Settings": { "CheckIntervalHours": 6, "IconPath": "app.ico" }
                }
                """.formatted(escapeJson(installFile.toString()), pageUrl);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class CapturedNotification implements UpdateService.UpdateNotifier {
        private String installedVersion;
        private String latestVersion;

        @Override
        public void notifyUpdate(String currentVersion, String latestVersion) {
            this.installedVersion = currentVersion;
            this.latestVersion = latestVersion;
        }
    }
}

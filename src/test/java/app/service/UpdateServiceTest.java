package app.service;

import app.config.AppConfig;
import app.testutil.StubGistLoader;
import app.testutil.TestConfigs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes da decisao de negocio: quando avisar o usuario e o que dizer.
 *
 * <p>Nenhum teste abre janela. A notificacao real e substituida por um
 * observador, e as versoes por valores fixos, de forma que a regra de decisao
 * seja verificada isoladamente.</p>
 */
class UpdateServiceTest {

    /** Registra as chamadas de notificacao sem abrir nenhuma janela. */
    private static final class ObservadorNotificacao implements UpdateService.UpdateNotifier {
        private final List<String> avisos = new ArrayList<>();

        @Override
        public void notifyUpdate(String currentVersion, String latestVersion) {
            avisos.add(currentVersion + " -> " + latestVersion);
        }

        int totalDeAvisos() {
            return avisos.size();
        }

        List<String> getAvisos() {
            return List.copyOf(avisos);
        }
    }

    /** Servico de versao instalada que devolve um valor fixo (sem disco). */
    private static final class VersaoInstaladaFixa extends EcfVersionService {
        private final String versao;

        VersaoInstaladaFixa(String versao) {
            this.versao = versao;
        }

        @Override
        public String getInstalledVersion(String path) {
            return versao;
        }
    }

    /** Servico da versao publicada que devolve um valor fixo (sem rede). */
    private static final class VersaoPublicadaFixa extends EcfLatestVersionService {
        private final String versao;

        VersaoPublicadaFixa(String versao) {
            this.versao = versao;
        }

        @Override
        public String getLatestVersion(String url, String className) {
            return versao;
        }
    }

    private static SettingsService settingsEm(Path tempDir) {
        AppConfig config = TestConfigs.build("C:\\SpedECF\\response.varfile",
                "https://www.gov.br/receitafederal/ecf", "rfb_subheader", 6, "EcfNotificador.ico");
        return new SettingsService(new StubGistLoader(config),
                tempDir.resolve("config").resolve("appSettings.json"));
    }

    private static UpdateService updateService(SettingsService settings,
                                               String instalada,
                                               String publicada,
                                               ObservadorNotificacao observador) {
        return new UpdateService(settings,
                new VersaoInstaladaFixa(instalada),
                new VersaoPublicadaFixa(publicada),
                observador);
    }

    @ParameterizedTest(name = "instalada {0} / publicada {1} => {2}")
    @MethodSource("cenariosDeComparacao")
    @DisplayName("Decide corretamente quando avisar o usuario")
    void decideQuandoAvisar(String instalada, String publicada,
                            UpdateService.CheckResult esperado, boolean deveAvisar,
                            @TempDir Path tempDir) {
        ObservadorNotificacao observador = new ObservadorNotificacao();
        UpdateService service = updateService(settingsEm(tempDir), instalada, publicada, observador);

        assertEquals(esperado, service.checkForUpdates());
        if (deveAvisar) {
            assertEquals(1, observador.totalDeAvisos(), "O usuario deveria ter sido avisado.");
            assertEquals(List.of(instalada + " -> " + publicada), observador.getAvisos());
        } else {
            assertEquals(0, observador.totalDeAvisos(), "Nao deveria haver aviso.");
        }
    }

    private static Stream<Arguments> cenariosDeComparacao() {
        return Stream.of(
                // Atualizacao real: avisa uma vez, com as duas versoes.
                Arguments.of("12.2.5", "12.2.6", UpdateService.CheckResult.UPDATE_AVAILABLE, true),
                Arguments.of("12.2.5", "13.0.0", UpdateService.CheckResult.UPDATE_AVAILABLE, true),
                Arguments.of("12.2", "12.2.1", UpdateService.CheckResult.UPDATE_AVAILABLE, true),

                // Instalada ja e a mais recente: nao avisa.
                Arguments.of("12.2.5", "12.2.5", UpdateService.CheckResult.UP_TO_DATE, false),
                Arguments.of("12.2", "12.2.0", UpdateService.CheckResult.UP_TO_DATE, false),
                Arguments.of("12.2.5", "12.2.5-beta", UpdateService.CheckResult.UP_TO_DATE, false),

                // Publicada e mais antiga (rollback no site): nao avisa para nao
                // induzir o usuario a regredir de versao.
                Arguments.of("12.2.6", "12.2.5", UpdateService.CheckResult.UP_TO_DATE, false),
                Arguments.of("13.0.0", "12.9.9", UpdateService.CheckResult.UP_TO_DATE, false)

                // O caso "nao conseguiu comparar" nao entra aqui porque usa
                // valores nulos; esta coberto nos testes dedicados abaixo.
        );
    }

    @Test
    @DisplayName("Versao instalada ausente nao gera aviso")
    void versaoInstaladaAusente(@TempDir Path tempDir) {
        ObservadorNotificacao observador = new ObservadorNotificacao();
        UpdateService service = updateService(settingsEm(tempDir), null, "12.2.6", observador);

        assertEquals(UpdateService.CheckResult.UNKNOWN, service.checkForUpdates());
        assertEquals(0, observador.totalDeAvisos());
    }

    @Test
    @DisplayName("Versao publicada ausente (site fora do ar) nao gera aviso")
    void versaoPublicadaAusente(@TempDir Path tempDir) {
        ObservadorNotificacao observador = new ObservadorNotificacao();
        UpdateService service = updateService(settingsEm(tempDir), "12.2.5", null, observador);

        assertEquals(UpdateService.CheckResult.UNKNOWN, service.checkForUpdates());
        assertEquals(0, observador.totalDeAvisos());
    }

    @Test
    @DisplayName("Nenhuma das duas versoes disponivel nao gera aviso")
    void nenhumaVersaoDisponivel(@TempDir Path tempDir) {
        ObservadorNotificacao observador = new ObservadorNotificacao();
        UpdateService service = updateService(settingsEm(tempDir), null, null, observador);

        assertEquals(UpdateService.CheckResult.UNKNOWN, service.checkForUpdates());
        assertEquals(0, observador.totalDeAvisos());
    }

    @Test
    @DisplayName("Verificacoes repetidas avisam apenas quando ha novidade em cada rodada")
    void verificacoesRepetidas(@TempDir Path tempDir) {
        ObservadorNotificacao observador = new ObservadorNotificacao();
        UpdateService service = updateService(settingsEm(tempDir), "12.2.5", "12.2.6", observador);

        service.checkForUpdates();
        service.checkForUpdates();

        // A decisao nao guarda estado: cada rodada com versao nova avisa de novo.
        assertEquals(2, observador.totalDeAvisos());
        assertTrue(observador.getAvisos().stream().allMatch("12.2.5 -> 12.2.6"::equals));
    }

    @Test
    @DisplayName("Os detalhes mostrados ao usuario usam as versoes comparadas")
    void detalhesUsamAsVersoesComparadas(@TempDir Path tempDir) {
        ObservadorNotificacao observador = new ObservadorNotificacao();
        UpdateService service = updateService(settingsEm(tempDir), "10.5.0", "11.0.0", observador);

        service.checkForUpdates();

        assertEquals(List.of("10.5.0 -> 11.0.0"), observador.getAvisos());
    }

    @Test
    @DisplayName("O servico completo com as implementacoes reais nao lanca excecao")
    void servicoComImplementacoesReaisNaoQuebra(@TempDir Path tempDir) {
        // Caminho inexistente e URL fora do ar: deve resultar em UNKNOWN, sem
        // derrubar a thread do agendador.
        AppConfig config = TestConfigs.build(
                tempDir.resolve("nao-existe.varfile").toString(),
                "https://host-que-nao-existe.invalid/ecf",
                "rfb_subheader", 6, "EcfNotificador.ico");
        SettingsService settings = new SettingsService(new StubGistLoader(config),
                tempDir.resolve("config").resolve("appSettings.json"));

        UpdateService service = new UpdateService(settings,
                new EcfVersionService(), new EcfLatestVersionService(),
                new ObservadorNotificacao());

        assertEquals(UpdateService.CheckResult.UNKNOWN, service.checkForUpdates());
    }
}

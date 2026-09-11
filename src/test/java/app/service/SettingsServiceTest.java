package app.service;

import app.config.AppConfig;
import app.testutil.StubGistLoader;
import app.testutil.TestConfigs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do servico de configuracao.
 *
 * <p>Cobrem as regras que mais impactam o usuario:</p>
 * <ul>
 *   <li>o Gist e a fonte de verdade enquanto o usuario nao mexer no intervalo;</li>
 *   <li>depois que o usuario escolhe um intervalo, a escolha dele prevalece;</li>
 *   <li>as escolhas sobrevivem ao reinicio do programa;</li>
 *   <li>Gist indisponivel nunca derruba a inicializacao.</li>
 * </ul>
 */
class SettingsServiceTest {

    private static final String JSON_GIST = """
            {
              "Ecf": {
                "InstallPath": "C:\\\\SpedECF\\\\response.varfile",
                "DownloadUrl": "https://www.gov.br/receitafederal/ecf",
                "VersionHtmlClass": "rfb_subheader"
              },
              "Settings": {
                "CheckIntervalHours": 6,
                "IconPath": "EcfNotificador.ico"
              }
            }
            """;

    /** Cria o servico isolado em um diretorio temporario. */
    private static SettingsService emDiretorio(Path tempDir, StubGistLoader loader) {
        return new SettingsService(loader, arquivoDe(tempDir));
    }

    private static Path arquivoDe(Path tempDir) {
        return tempDir.resolve("config").resolve("appSettings.json");
    }

    // ===================== Valores recebidos do Gist =====================

    @Test
    @DisplayName("Aplica no cache local os valores recebidos do Gist")
    void aplicaValoresDoGist(@TempDir Path tempDir) {
        SettingsService service = emDiretorio(tempDir, StubGistLoader.comJson(JSON_GIST));

        assertTrue(service.getEcfInstallPath().contains("response.varfile"));
        assertEquals("https://www.gov.br/receitafederal/ecf", service.getDownloadUrl());
        assertEquals("rfb_subheader", service.getVersionHtmlClass());
        assertEquals(6, service.getCheckIntervalHours());
        assertEquals("EcfNotificador.ico", service.getIconPath());
    }

    @Test
    @DisplayName("O Gist e gravado no cache local para uso offline")
    void gistEhGravadoNoCacheLocal(@TempDir Path tempDir) {
        SettingsService service = emDiretorio(tempDir, StubGistLoader.comJson(JSON_GIST));

        assertTrue(Files.exists(service.getSettingsPath()),
                "O cache local deveria ter sido gravado apos a leitura do Gist.");

        // Um novo servico sem Gist (simulando a proxima inicializacao offline)
        // deve enxergar os valores vindos do Gist.
        SettingsService offline = emDiretorio(tempDir, StubGistLoader.indisponivel());
        assertEquals("https://www.gov.br/receitafederal/ecf", offline.getDownloadUrl());
        assertEquals(6, offline.getCheckIntervalHours());
    }

    @Test
    @DisplayName("Gist indisponivel mantem o programa funcional com os padroes")
    void gistIndisponivelUsaValoresPadrao(@TempDir Path tempDir) {
        SettingsService service = emDiretorio(tempDir, StubGistLoader.indisponivel());

        assertEquals("", service.getEcfInstallPath());
        assertEquals("", service.getDownloadUrl());
        assertEquals("rfb_subheader", service.getVersionHtmlClass());
        assertEquals(6, service.getCheckIntervalHours());
        assertEquals("EcfNotificador.ico", service.getIconPath());
    }

    @Test
    @DisplayName("Arquivo de configuracao corrompido nao derruba a inicializacao")
    void arquivoCorrompidoNaoDerruba(@TempDir Path tempDir) throws IOException {
        Path arquivo = tempDir.resolve("config").resolve("appSettings.json");
        Files.createDirectories(arquivo.getParent());
        Files.writeString(arquivo, "{ isso nao e json valido", StandardCharsets.UTF_8);

        SettingsService service = new SettingsService(StubGistLoader.indisponivel(), arquivo);

        // Cai para os padroes em vez de propagar a falha.
        assertEquals(6, service.getCheckIntervalHours());
        assertEquals("EcfNotificador.ico", service.getIconPath());
    }

    // ===================== Precedencia do intervalo =====================

    @Test
    @DisplayName("Enquanto o usuario nao mexer, o Gist define o intervalo")
    void gistDefineIntervaloEnquantoUsuarioNaoMexer(@TempDir Path tempDir) {
        AppConfig gist = TestConfigs.build("caminho", "https://a.test", "cls", 12, null);
        SettingsService service = new SettingsService(new StubGistLoader(gist), arquivoDe(tempDir));

        assertEquals(12, service.getCheckIntervalHours());
    }

    @Test
    @DisplayName("Escolha do usuario prevalece sobre o Gist")
    void escolhaDoUsuarioPrevalece(@TempDir Path tempDir) {
        AppConfig gist = TestConfigs.build("caminho", "https://a.test", "cls", 12, null);
        SettingsService service = new SettingsService(new StubGistLoader(gist), arquivoDe(tempDir));

        service.setCheckIntervalHours(3);

        // Uma nova leitura do Gist (que traz 12) nao pode desfazer a escolha.
        service.refreshFromGist();
        assertEquals(3, service.getCheckIntervalHours());
    }

    @Test
    @DisplayName("A escolha do usuario sobrevive ao reinicio do programa")
    void escolhaDoUsuarioSobreviveAoReinicio(@TempDir Path tempDir) {
        AppConfig gist = TestConfigs.build("caminho", "https://a.test", "cls", 12, null);
        Path arquivo = arquivoDe(tempDir);

        new SettingsService(new StubGistLoader(gist), arquivo).setCheckIntervalHours(4);

        // "Reinicia" o programa: novo servico, mesmo arquivo, Gist ainda com 12.
        SettingsService reiniciado = new SettingsService(new StubGistLoader(gist), arquivo);
        assertEquals(4, reiniciado.getCheckIntervalHours(),
                "A escolha do usuario tem de ser gravada no arquivo local.");
    }

    @Test
    @DisplayName("O Gist continua atualizando os campos que o usuario nao controla")
    void gistAtualizaCamposNaoControlados(@TempDir Path tempDir) {
        AppConfig inicial = TestConfigs.build("caminho-antigo", "https://antigo.test", "cls", 6, null);
        Path arquivo = arquivoDe(tempDir);
        SettingsService service = new SettingsService(new StubGistLoader(inicial), arquivo);

        service.setCheckIntervalHours(2);

        // O Gist muda a URL e o caminho, mas nao o intervalo (o usuario escolheu).
        AppConfig novo = TestConfigs.build(
                "caminho-novo", "https://novo.test", "cls", 8, "novo-icone.ico");
        SettingsService comNovoGist = new SettingsService(new StubGistLoader(novo), arquivo);

        assertEquals("https://novo.test", comNovoGist.getDownloadUrl());
        assertEquals("caminho-novo", comNovoGist.getEcfInstallPath());
        assertEquals("novo-icone.ico", comNovoGist.getIconPath());
        assertEquals(2, comNovoGist.getCheckIntervalHours(),
                "O intervalo escolhido pelo usuario nao pode ser sobrescrito pelo Gist.");
    }

    // ===================== Normalizacao de valores invalidos =====================

    @ParameterizedTest(name = "intervalo invalido {0} vira o padrao")
    @ValueSource(ints = {0, -1, -100})
    @DisplayName("Intervalo menor que 1 e corrigido para o padrao")
    void intervaloInvalidoViraPadrao(int intervalo, @TempDir Path tempDir) {
        SettingsService service = new SettingsService(StubGistLoader.indisponivel(), arquivoDe(tempDir));

        service.setCheckIntervalHours(intervalo);

        assertEquals(6, service.getCheckIntervalHours(),
                "Um intervalo invalido deve cair no padrao de 6 horas.");
    }

    @Test
    @DisplayName("Intervalo ausente no JSON usa o padrao de 6 horas")
    void intervaloAusenteUsaPadrao(@TempDir Path tempDir) {
        AppConfig semIntervalo = TestConfigs.build("caminho", "https://a.test", "cls", null, null);
        SettingsService service = new SettingsService(new StubGistLoader(semIntervalo), arquivoDe(tempDir));

        assertEquals(6, service.getCheckIntervalHours());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("Icone nulo ou em branco cai no padrao")
    void iconeInvalidoUsaPadrao(String icone, @TempDir Path tempDir) {
        AppConfig config = TestConfigs.build("caminho", "https://a.test", "cls", 6, icone);
        SettingsService service = new SettingsService(new StubGistLoader(config), arquivoDe(tempDir));

        assertEquals("EcfNotificador.ico", service.getIconPath());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("Classe HTML nula ou em branco cai no padrao")
    void classeHtmlInvalidaUsaPadrao(String classe, @TempDir Path tempDir) {
        AppConfig config = TestConfigs.build("caminho", "https://a.test", classe, 6, null);
        SettingsService service = new SettingsService(new StubGistLoader(config), arquivoDe(tempDir));

        assertEquals("rfb_subheader", service.getVersionHtmlClass());
    }

    @Test
    @DisplayName("Icone definido no Gist prevalece sobre o padrao")
    void iconeDoGistEhAplicado(@TempDir Path tempDir) {
        AppConfig config = TestConfigs.build("caminho", "https://a.test", "cls", 6, "meu-icone.ico");
        SettingsService service = new SettingsService(new StubGistLoader(config), arquivoDe(tempDir));

        assertEquals("meu-icone.ico", service.getIconPath());
    }

    @Test
    @DisplayName("Icone em branco vindo do Gist nao apaga o valor atual")
    void iconeEmBrancoNoGistNaoApagaValor(@TempDir Path tempDir) {
        AppConfig comIcone = TestConfigs.build("caminho", "https://a.test", "cls", 6, "atual.ico");
        Path arquivo = arquivoDe(tempDir);
        SettingsService service = new SettingsService(new StubGistLoader(comIcone), arquivo);
        assertEquals("atual.ico", service.getIconPath());

        AppConfig semIcone = TestConfigs.build("caminho", "https://a.test", "cls", 6, "  ");
        SettingsService comGistVazio = new SettingsService(new StubGistLoader(semIcone), arquivo);
        comGistVazio.refreshFromGist();

        assertEquals("atual.ico", comGistVazio.getIconPath());
    }

    // ===================== JSON gravado =====================

    @Test
    @DisplayName("O arquivo local grava a marca de intervalo escolhido pelo usuario")
    void arquivoLocalGravaMarcaDoUsuario(@TempDir Path tempDir) throws IOException {
        Path arquivo = arquivoDe(tempDir);
        SettingsService service = new SettingsService(StubGistLoader.indisponivel(), arquivo);

        service.setCheckIntervalHours(9);

        String json = Files.readString(arquivo, StandardCharsets.UTF_8);
        assertTrue(json.contains("IntervalOverriddenByUser"),
                "A marca precisa ir para o arquivo, senao a escolha se perde no reinicio.");
        assertTrue(json.contains("\"CheckIntervalHours\" : 9"),
                "O intervalo escolhido deveria estar gravado no arquivo.");
    }

    @ParameterizedTest(name = "configuracao {index}")
    @MethodSource("configuracoesCompletas")
    @DisplayName("Cada campo do Gist chega ao servico sem alteracao")
    void camposChegamSemAlteracao(String downloadUrl, String installPath,
                                  String htmlClass, int intervalo, String icone,
                                  @TempDir Path tempDir) {
        AppConfig config = TestConfigs.build(installPath, downloadUrl, htmlClass, intervalo, icone);
        SettingsService service = new SettingsService(new StubGistLoader(config), arquivoDe(tempDir));

        assertEquals(installPath, service.getEcfInstallPath());
        assertEquals(downloadUrl, service.getDownloadUrl());
        assertEquals(htmlClass, service.getVersionHtmlClass());
        assertEquals(intervalo, service.getCheckIntervalHours());
        assertEquals(icone, service.getIconPath());
    }

    private static Stream<Arguments> configuracoesCompletas() {
        return Stream.of(
                Arguments.of("https://a.test", "C:\\A\\response.varfile", "cls", 1, "a.ico"),
                Arguments.of("https://b.test", "D:\\B\\response.varfile", "outra", 24, "b.png"),
                Arguments.of("https://c.test", "E:\\C\\response.varfile", "rfb_subheader", 168, "c.ico")
        );
    }

    @Test
    @DisplayName("O caminho do arquivo local e absoluto")
    void caminhoDoArquivoEhAbsoluto(@TempDir Path tempDir) {
        SettingsService service = emDiretorio(tempDir, StubGistLoader.indisponivel());

        assertNotNull(service.getSettingsPath());
        assertTrue(service.getSettingsPath().isAbsolute());
    }

    @Test
    @DisplayName("refreshFromGist com Gist indisponivel nao altera a configuracao")
    void refreshSemGistNaoAltera(@TempDir Path tempDir) {
        AppConfig config = TestConfigs.build("caminho", "https://a.test", "cls", 5, "x.ico");
        SettingsService service = new SettingsService(new StubGistLoader(config), arquivoDe(tempDir));

        SettingsService somenteLocal = new SettingsService(
                new StubGistLoader(config), arquivoDe(tempDir));
        assertEquals(5, somenteLocal.getCheckIntervalHours());

        // Cache local ja gravado; agora o Gist sai do ar.
        SettingsService offline = new SettingsService(StubGistLoader.indisponivel(), arquivoDe(tempDir));
        assertEquals("https://a.test", offline.getDownloadUrl());
        assertEquals(5, offline.getCheckIntervalHours());

        service.refreshFromGist();
        assertEquals(5, service.getCheckIntervalHours());
        assertFalse(service.getEcfInstallPath().isEmpty());
    }

    // ===================== Protecao contra edicao parcial no Gist =====================

    @Nested
    @DisplayName("Gist editado de forma parcial ou incompleta")
    class GistParcial {

        /** Cache ja populado, representando uma instalacao que ja funcionava. */
        private SettingsService comCacheBom(Path tempDir, String jsonDoGist) {
            AppConfig inicial = TestConfigs.build("C:\\SpedECF\\response.varfile",
                    "https://www.gov.br/ecf", "rfb_subheader", 6, "icone-bom.ico");
            Path arquivo = arquivoDe(tempDir);

            SettingsService primeiro = new SettingsService(new StubGistLoader(inicial), arquivo);
            assertFalse(primeiro.getDownloadUrl().isEmpty());

            SettingsService segundo = new SettingsService(
                    new StubGistLoader(TestConfigs.fromJson(jsonDoGist)), arquivo);
            segundo.refreshFromGist();
            return segundo;
        }

        @Test
        @DisplayName("Gist que omite o bloco \"Ecf\" nao apaga a configuracao boa")
        void gistSemBlocoEcfNaoApaga(@TempDir Path tempDir) {
            SettingsService service = comCacheBom(tempDir,
                    "{\"Settings\": {\"CheckIntervalHours\": 12}}");

            assertEquals("https://www.gov.br/ecf", service.getDownloadUrl(),
                    "Uma chave ausente no Gist nao pode zerar o valor atual.");
            assertTrue(service.getEcfInstallPath().contains("response.varfile"));
            assertEquals("rfb_subheader", service.getVersionHtmlClass());
            assertEquals(12, service.getCheckIntervalHours(),
                    "O campo que veio no Gist deve ser aplicado normalmente.");
        }

        @Test
        @DisplayName("Gist que omite o bloco \"Settings\" preserva icone e intervalo")
        void gistSemBlocoSettingsNaoApaga(@TempDir Path tempDir) {
            SettingsService service = comCacheBom(tempDir,
                    "{\"Ecf\": {\"DownloadUrl\": \"https://novo.test\"}}");

            assertEquals("https://novo.test", service.getDownloadUrl());
            assertEquals("icone-bom.ico", service.getIconPath());
            assertEquals(6, service.getCheckIntervalHours());
        }

        @Test
        @DisplayName("Gist com blocos vazios nao apaga nada")
        void gistComBlocosVaziosNaoApaga(@TempDir Path tempDir) {
            SettingsService service = comCacheBom(tempDir, "{\"Ecf\": {}, \"Settings\": {}}");

            assertEquals("https://www.gov.br/ecf", service.getDownloadUrl());
            assertEquals("icone-bom.ico", service.getIconPath());
            assertTrue(service.getEcfInstallPath().contains("response.varfile"));
        }

        @Test
        @DisplayName("Valores em branco no Gist nao apagam os valores atuais")
        void gistComValoresEmBrancoNaoApaga(@TempDir Path tempDir) {
            SettingsService service = comCacheBom(tempDir, """
                    {
                      "Ecf": { "DownloadUrl": "   ", "InstallPath": "" },
                      "Settings": { "IconPath": "" }
                    }
                    """);

            assertEquals("https://www.gov.br/ecf", service.getDownloadUrl());
            assertEquals("icone-bom.ico", service.getIconPath());
            assertTrue(service.getEcfInstallPath().contains("response.varfile"));
        }

        @Test
        @DisplayName("O cache em disco nao e sobrescrito com valores apagados")
        void cacheNaoGuardaValoresApagados(@TempDir Path tempDir) throws IOException {
            Path arquivo = arquivoDe(tempDir);
            comCacheBom(tempDir, "{\"Settings\": {\"CheckIntervalHours\": 12}}");

            String json = Files.readString(arquivo, StandardCharsets.UTF_8);

            assertFalse(json.contains("\"DownloadUrl\" : \"\""),
                    "O cache nao pode guardar uma URL vazia vinda de um Gist parcial.");
            assertTrue(json.contains("https://www.gov.br/ecf"),
                    "O cache deve manter a URL boa que ja existia.");
        }

        @Test
        @DisplayName("Gist valido altera a URL, a classe HTML e o caminho na hora")
        void mudancaDoGistEhAplicada(@TempDir Path tempDir) {
            SettingsService service = comCacheBom(tempDir, """
                    {
                      "Ecf": {
                        "DownloadUrl": "https://www.gov.br/receitafederal/novo",
                        "InstallPath": "D:\\\\ECF\\\\outro\\\\response.varfile",
                        "VersionHtmlClass": "nova_classe"
                      }
                    }
                    """);

            assertEquals("https://www.gov.br/receitafederal/novo", service.getDownloadUrl());
            assertEquals("D:\\ECF\\outro\\response.varfile", service.getEcfInstallPath());
            assertEquals("nova_classe", service.getVersionHtmlClass());
        }
    }

    // ===================== Deteccao de mudanca =====================

    @Nested
    @DisplayName("Retorno de refreshFromGist")
    class SinalDeMudanca {

        @Test
        @DisplayName("Mudanca publicada no Gist sinaliza mudanca")
        void gistAlteradoSinalizaMudanca(@TempDir Path tempDir) {
            AppConfig inicial = TestConfigs.build("caminho", "https://a.test", "cls", 6, "i.ico");
            StubGistLoader stub = new StubGistLoader(inicial);
            SettingsService service = new SettingsService(stub, arquivoDe(tempDir));

            // Simula a edicao do Gist no GitHub entre duas recargas.
            stub.publicar(TestConfigs.build("caminho", "https://b.test", "cls", 6, "i.ico"));

            assertTrue(service.refreshFromGist(), "A URL mudou, entao houve alteracao.");
            assertEquals("https://b.test", service.getDownloadUrl());
        }

        @Test
        @DisplayName("Gist inalterado nao sinaliza mudanca")
        void gistInalteradoNaoSinaliza(@TempDir Path tempDir) {
            AppConfig config = TestConfigs.build("caminho", "https://a.test", "cls", 6, "i.ico");
            SettingsService service = new SettingsService(
                    new StubGistLoader(config), arquivoDe(tempDir));

            assertFalse(service.refreshFromGist(),
                    "Recarregar o mesmo conteudo nao deve contar como mudanca.");
        }

        @Test
        @DisplayName("Gist indisponivel nao sinaliza mudanca")
        void gistIndisponivelNaoSinaliza(@TempDir Path tempDir) {
            SettingsService service = emDiretorio(tempDir, StubGistLoader.indisponivel());

            assertFalse(service.refreshFromGist());
        }
    }
}

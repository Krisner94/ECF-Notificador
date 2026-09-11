package app.service;

import app.config.AppConfig;
import app.config.EcfConfig;
import app.config.SettingsData;
import app.testutil.TestConfigs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do carregador de configuracao do Gist.
 *
 * <p>Nenhum teste acessa a rede: o foco e validar o comportamento tolerante a
 * falha (Gist fora do ar nao pode derrubar o programa), a URL configurada e o
 * parsing do JSON publicado.</p>
 */
class GistSettingsLoaderTest {

    private static final String JSON_PUBLICADO = """
            {
              "Ecf": {
                "InstallPath": "C:\\\\Arquivos de Programas RFB\\\\Programas SPED\\\\SpedECF\\\\.install4j\\\\response.varfile",
                "DownloadUrl": "https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/download/sped/ecf",
                "VersionHtmlClass": "rfb_subheader"
              },
              "Settings": {
                "CheckIntervalHours": 6,
                "IconPath": "EcfNotificador.ico"
              }
            }
            """;

    // ===================== URL =====================

    @Test
    @DisplayName("URL padrao aponta para a versao raw do Gist")
    void urlPadraoApontaParaOGist() {
        GistSettingsLoader loader = new GistSettingsLoader();

        assertNotNull(loader.getGistUrl());
        assertTrue(loader.getGistUrl().startsWith("https://gist.githubusercontent.com/"),
                "A URL deve ser a versao 'raw' do Gist, nao a pagina HTML.");
        assertTrue(loader.getGistUrl().endsWith("appSettings.json"),
                "A URL deve apontar para o arquivo appSettings.json.");
    }

    @ParameterizedTest(name = "url informada: {0}")
    @ValueSource(strings = {
            "https://gist.githubusercontent.com/outro-usuario/abc123/raw/appSettings.json",
            "https://exemplo.test/config.json"
    })
    @DisplayName("URL injetada e preservada integralmente")
    void urlInjetadaEhPreservada(String url) {
        assertEquals(url, new GistSettingsLoader(url).getGistUrl());
    }

    // ===================== Tolerancia a falha =====================

    @ParameterizedTest(name = "url sem resposta: {0}")
    @ValueSource(strings = {
            "nao-e-uma-url-valida",
            "https://host-que-nao-existe.invalid/appSettings.json",
            "http://localhost:1/appSettings.json"
    })
    @DisplayName("URL invalida ou fora do ar retorna null em vez de lancar excecao")
    void urlInvalidaRetornaNull(String url) {
        // O contrato e nao lancar: qualquer excecao aqui quebraria a inicializacao.
        assertNull(new GistSettingsLoader(url).load());
    }

    // ===================== Parsing =====================

    @Test
    @DisplayName("Configuracao desserializada preserva os valores do JSON do Gist")
    void configuracaoDesserializadaPreservaValores() {
        AppConfig config = GistSettingsLoader.parse(JSON_PUBLICADO);

        EcfConfig ecf = config.getEcf();
        assertNotNull(ecf);
        assertTrue(ecf.getInstallPath().contains("response.varfile"));
        assertTrue(ecf.getDownloadUrl().startsWith("https://www.gov.br/receitafederal"));
        assertEquals("rfb_subheader", ecf.getVersionHtmlClass());

        SettingsData settings = config.getSettings();
        assertNotNull(settings);
        assertEquals(6, settings.getCheckIntervalHours());
        assertEquals("EcfNotificador.ico", settings.getIconPath());
    }

    @ParameterizedTest(name = "json invalido (indice {index})")
    @ValueSource(strings = {
            "{ nao e json }",
            "texto puro",
            "{\"Ecf\": }",
            "[]"
    })
    @DisplayName("JSON invalido retorna null, sem propagar excecao")
    void jsonInvalidoRetornaNull(String json) {
        assertNull(GistSettingsLoader.parse(json));
    }

    @Test
    @DisplayName("Campo desconhecido no Gist e ignorado, sem falhar a leitura")
    void campoDesconhecidoEhIgnorado() {
        String json = """
                {
                  "Ecf": { "DownloadUrl": "https://exemplo.test" },
                  "Settings": { "CheckIntervalHours": 8 },
                  "CampoNovoQueAindaNaoExiste": true
                }
                """;

        AppConfig config = GistSettingsLoader.parse(json);

        assertNotNull(config);
        assertEquals("https://exemplo.test", config.getEcf().getDownloadUrl());
        assertEquals(8, config.getSettings().getCheckIntervalHours());
        assertFalse(config.getSettings().isIntervalOverriddenByUser());
    }

    @ParameterizedTest(name = "json: {0}")
    @MethodSource("jsonMinimos")
    @DisplayName("Chave ausente no JSON fica nula, para o merge preservar o valor atual")
    void jsonParcialDeixaCamposAusentesNulos(String json, String downloadUrlEsperada, Integer intervaloEsperado) {
        AppConfig config = GistSettingsLoader.parse(json);

        assertNotNull(config);
        assertEquals(downloadUrlEsperada, config.getEcf().getDownloadUrl());
        assertEquals(intervaloEsperado, config.getSettings().getCheckIntervalHours());
    }

    private static Stream<Arguments> jsonMinimos() {
        return Stream.of(
                // Apenas o Ecf: o intervalo nao veio, entao fica nulo.
                Arguments.of("{\"Ecf\": {\"DownloadUrl\": \"https://a.test\"}}", "https://a.test", null),
                // Apenas o Settings: a URL nao veio, entao fica nula.
                Arguments.of("{\"Settings\": {\"CheckIntervalHours\": 12}}", null, 12),
                // Objeto vazio: nada veio, tudo nulo.
                Arguments.of("{}", null, null)
        );
    }

    @Test
    @DisplayName("JSON nulo retorna null")
    void jsonNuloRetornaNull() {
        assertNull(GistSettingsLoader.parse(null));
    }

    @Test
    @DisplayName("Configuracao lida do Gist tambem desserializa pelo helper de teste")
    void helperDeTesteUsaMesmasRegras() {
        // Garante que o fixture de teste nao diverge do parser de producao.
        AppConfig viaParsing = GistSettingsLoader.parse(JSON_PUBLICADO);
        AppConfig viaHelper = TestConfigs.fromJson(JSON_PUBLICADO);

        assertEquals(viaParsing.getEcf().getInstallPath(), viaHelper.getEcf().getInstallPath());
        assertEquals(viaParsing.getSettings().getCheckIntervalHours(),
                viaHelper.getSettings().getCheckIntervalHours());
    }
}

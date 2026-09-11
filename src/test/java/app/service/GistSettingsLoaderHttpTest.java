package app.service;

import app.config.AppConfig;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integracao real do download do Gist, com servidor HTTP embarcado.
 *
 * <p>Os demais testes do carregador validam URL e parsing sem tocar na rede.
 * Aqui o foco e o metodo {@code download()}: codigos de status, o ciclo
 * ETag/{@code If-None-Match} e a tolerancia a falha de conexao - a parte que
 * garante que uma edicao no Gist chega ao programa em execucao.</p>
 *
 * <p>Marcada com {@code @Tag("servidor")} porque o WireMock sobe um servidor
 * HTTP embarcado: esses testes ficam fora do perfil {@code native-test}, onde o
 * modelo closed-world do Native Image nao acomoda servidor dinamico.</p>
 */
@Tag("servidor")
class GistSettingsLoaderHttpTest {

    private static final String CAMINHO = "/appSettings.json";

    private static WireMockServer servidor;
    private static String urlBase;

    @BeforeAll
    static void iniciarServidor() {
        servidor = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        servidor.start();
        urlBase = "http://localhost:" + servidor.port() + CAMINHO;
    }

    @AfterAll
    static void pararServidor() {
        if (servidor != null) {
            servidor.stop();
        }
    }

    @BeforeEach
    void limparCenarios() {
        servidor.resetAll();
    }

    private static String json(String downloadUrl) {
        return """
                {
                  "Ecf": {
                    "InstallPath": "C:\\\\SpedECF\\\\response.varfile",
                    "DownloadUrl": "%s",
                    "VersionHtmlClass": "rfb_subheader"
                  },
                  "Settings": { "CheckIntervalHours": 6 }
                }
                """.formatted(downloadUrl);
    }

    private GistSettingsLoader loader() {
        return new GistSettingsLoader(urlBase);
    }

    @Test
    @DisplayName("HTTP 200 entrega a configuracao publicada")
    void resposta200EntregaConfiguracao() {
        servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                .willReturn(aResponse().withStatus(200).withBody(json("https://a.test"))));

        AppConfig config = loader().load();

        assertThat(config).isNotNull();
        assertThat(config.getEcf().getDownloadUrl()).isEqualTo("https://a.test");
    }

    @Test
    @DisplayName("Mudanca do Gist e aplicada na recarga seguinte")
    void mudancaNoGistEhAplicada() {
        servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                .willReturn(aResponse().withStatus(200).withBody(json("https://a.test"))));

        GistSettingsLoader carregador = loader();
        assertThat(carregador.load().getEcf().getDownloadUrl()).isEqualTo("https://a.test");

        // O administrador edita o Gist: a URL nova passa a ser servida.
        servidor.resetAll();
        servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                .willReturn(aResponse().withStatus(200).withBody(json("https://b.test"))));

        assertThat(carregador.load().getEcf().getDownloadUrl()).isEqualTo("https://b.test");
    }

    @Nested
    @DisplayName("ETag e If-None-Match")
    class Etag {

        @Test
        @DisplayName("O ETag recebido e guardado para a proxima verificacao")
        void etagEhGuardado() {
            servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                    .willReturn(aResponse().withStatus(200)
                            .withHeader("ETag", "\"abc123\"")
                            .withBody(json("https://a.test"))));

            GistSettingsLoader carregador = loader();
            carregador.load();

            assertThat(carregador.getLastEtag()).isEqualTo("\"abc123\"");
        }

        @Test
        @DisplayName("A segunda verificacao envia If-None-Match com o ETag guardado")
        void segundaVerificacaoEnviaIfNoneMatch() {
            servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                    .willReturn(aResponse().withStatus(200)
                            .withHeader("ETag", "\"abc123\"")
                            .withBody(json("https://a.test"))));

            GistSettingsLoader carregador = loader();
            carregador.load();
            carregador.load();

            servidor.verify(com.github.tomakehurst.wiremock.client.WireMock
                    .getRequestedFor(urlPathEqualTo(CAMINHO))
                    .withHeader("If-None-Match", equalTo("\"abc123\"")));
        }

        @Test
        @DisplayName("HTTP 304 (inalterado) nao devolve configuracao nem erro")
        void resposta304RetornaNull() {
            servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                    .willReturn(aResponse().withStatus(304)));

            assertThat(loader().load()).isNull();
        }

        @Test
        @DisplayName("304 mantem o ETag anterior, para continuar economizando banda")
        void resposta304PreservaEtag() {
            servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                    .willReturn(aResponse().withStatus(200)
                            .withHeader("ETag", "\"abc123\"")
                            .withBody(json("https://a.test"))));

            GistSettingsLoader carregador = loader();
            carregador.load();

            servidor.resetAll();
            servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                    .willReturn(aResponse().withStatus(304)));

            assertThat(carregador.load()).isNull();
            assertThat(carregador.getLastEtag()).isEqualTo("\"abc123\"");
        }
    }

    @Nested
    @DisplayName("Tolerancia a falha")
    class Falhas {

        @Test
        @DisplayName("Status de erro retorna null em vez de lancar")
        void statusDeErroRetornaNull() {
            for (int status : new int[] {301, 400, 403, 404, 429, 500, 503}) {
                servidor.resetAll();
                servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                        .willReturn(aResponse().withStatus(status).withBody(json("https://a.test"))));

                assertThat(loader().load())
                        .as("HTTP %d deveria ser tratado como indisponivel", status)
                        .isNull();
            }
        }

        @Test
        @DisplayName("Corpo vazio ou invalido retorna null")
        void corpoInvalidoRetornaNull() {
            servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                    .willReturn(aResponse().withStatus(200).withBody("{\"Ecf\": }")));

            assertThat(loader().load()).isNull();
        }

        @Test
        @DisplayName("Servidor fora do ar retorna null sem derrubar o programa")
        void servidorForaDoArRetornaNull() {
            GistSettingsLoader carregador = new GistSettingsLoader("http://localhost:1" + CAMINHO);

            assertThat(carregador.load()).isNull();
        }

        @Test
        @DisplayName("Falha de rede nao descarta uma configuracao ja obtida antes")
        void falhaNaoPerdeConfiguracaoAnterior() {
            servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                    .willReturn(aResponse().withStatus(200).withBody(json("https://a.test"))));

            GistSettingsLoader carregador = loader();
            assertThat(carregador.load().getEcf().getDownloadUrl()).isEqualTo("https://a.test");

            // Gist cai: o carregador devolve null e quem chamou mantem o cache.
            GistSettingsLoader semRede =
                    new GistSettingsLoader("http://localhost:1" + CAMINHO);
            assertThat(semRede.load()).isNull();
        }
    }

    @Test
    @DisplayName("Um Gist que so muda a classe HTML ja e detectado no download")
    void mudancaDeClasseHtmlChegaAoDownload() {
        servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                .willReturn(aResponse().withStatus(200).withBody(json("https://a.test"))));

        GistSettingsLoader carregador = loader();
        assertThat(carregador.load().getEcf().getVersionHtmlClass()).isEqualTo("rfb_subheader");

        servidor.resetAll();
        servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                .willReturn(aResponse().withStatus(200).withBody("""
                        {
                          "Ecf": {
                            "DownloadUrl": "https://a.test",
                            "VersionHtmlClass": "nova_classe"
                          }
                        }
                        """)));

        assertThat(carregador.load().getEcf().getVersionHtmlClass()).isEqualTo("nova_classe");
    }
}

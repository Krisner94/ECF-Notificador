package app.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Modulo 2 - Cliente HTTP GET, resiliencia e WireMock.
 *
 * <p>O servidor da Receita Federal e simulado localmente: cada resposta e
 * devolvida de forma deterministica, sem depender da internet. Isso permite
 * exercitar os codigos 200/404/500/503, o corpo vazio, o timeout e a queda de
 * conexao - cenarios que seriam impossiveis de reproduzir de forma confiavel
 * contra o servidor real.</p>
 *
 * <p>Marcada com {@code @Tag("servidor")} porque o WireMock sobe um servidor
 * HTTP embarcado: esses testes ficam fora do perfil {@code native-test}, onde o
 * modelo closed-world do Native Image nao acomoda servidor dinamico.</p>
 */
@Tag("servidor")
class EcfLatestVersionServiceHttpTest {

    private static final String CAMINHO = "/ecf";
    private static final String CLASSE = "rfb_subheader";

    private static WireMockServer servidor;
    private static String urlBase;
    private static HttpClient cliente;

    @BeforeAll
    static void iniciarServidor() {
        servidor = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        servidor.start();
        urlBase = "http://localhost:" + servidor.port() + CAMINHO;

        // Timeout curto: o teste de latencia nao pode deixar a suite lenta.
        cliente = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
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

    private EcfLatestVersionService servico() {
        return new EcfLatestVersionService(cliente);
    }

    private static String pagina(String versao) {
        return "<html><body><h3 class=\"rfb_subheader\">" + versao + "</h3></body></html>";
    }

    // ===================== Codigos de status =====================

    @ParameterizedTest(name = "HTTP {0} resulta em null")
    @ValueSource(ints = {301, 400, 401, 403, 404, 429, 500, 502, 503, 504})
    @DisplayName("Qualquer resposta que nao seja 200 e tratada como indisponivel")
    void respostaNao200RetornaNull(int status) {
        servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                .willReturn(aResponse().withStatus(status).withBody(pagina("12.2.6"))));

        assertThat(servico().getLatestVersion(urlBase, CLASSE)).isNull();
    }

    @Test
    @DisplayName("HTTP 200 com HTML valido extrai a versao")
    void resposta200ExtraiVersao() {
        servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=UTF-8")
                        .withBody(pagina("12.2.6"))));

        assertThat(servico().getLatestVersion(urlBase, CLASSE)).isEqualTo("12.2.6");
    }

    @Test
    @DisplayName("HTTP 200 com HTML sem a classe retorna null")
    void resposta200SemClasseRetornaNull() {
        servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                .willReturn(aResponse().withStatus(200).withBody("<html><body>vazio</body></html>")));

        assertThat(servico().getLatestVersion(urlBase, CLASSE)).isNull();
    }

    @ParameterizedTest(name = "corpo vazio definido por {0}")
    @ValueSource(strings = {"vazio", "espacos"})
    @DisplayName("HTTP 200 sem corpo util retorna null")
    void resposta200SemCorpoRetornaNull(String tipo) {
        String corpo = "vazio".equals(tipo) ? "" : "   ";
        servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                .willReturn(aResponse().withStatus(200).withBody(corpo)));

        assertThat(servico().getLatestVersion(urlBase, CLASSE)).isNull();
    }

    @Test
    @DisplayName("O body e enviado com acentos preservados (UTF-8)")
    void acentosSaoPreservados() {
        servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/html; charset=UTF-8")
                        .withBody("<h3 class=\"rfb_subheader\">Vers\u00e3o 12.2.6</h3>")));

        assertThat(servico().getLatestVersion(urlBase, CLASSE)).isEqualTo("12.2.6");
    }

    // ===================== Falhas de transporte =====================

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', value = {
            "EMPTY_RESPONSE      | EMPTY_RESPONSE",
            "CONNECTION_RESET    | CONNECTION_RESET_BY_PEER",
            "MALFORMED_RESPONSE  | MALFORMED_RESPONSE_CHUNK",
    })
    @DisplayName("Falha de transporte retorna null, sem propagar excecao")
    void falhaDeTransporteRetornaNull(String descricao, String tipoFault) {
        servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                .willReturn(aResponse().withFault(Fault.valueOf(tipoFault))));

        assertThat(servico().getLatestVersion(urlBase, CLASSE)).isNull();
    }

    @Test
    @DisplayName("Latencia acima do timeout da requisicao retorna null")
    void timeoutRetornaNull() {
        // O servidor responde, mas demora bem mais que o limite do servico.
        servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                .willReturn(aResponse().withStatus(200)
                        .withFixedDelay(3000)
                        .withBody(pagina("12.2.6"))));

        // O timeout precisa ser da REQUISICAO (nao so da conexao): o cliente
        // HTTP limita apenas o connect, entao sem isso a leitura ficaria
        // pendurada. Aqui o limite e curto para o teste ser rapido.
        EcfLatestVersionService servico =
                new EcfLatestVersionService(cliente, new EcfPageParser(), Duration.ofMillis(300));

        assertThat(servico.getLatestVersion(urlBase, CLASSE)).isNull();
    }

    @Test
    @DisplayName("Resposta dentro do timeout e lida normalmente")
    void respostaDentroDoTimeoutEhLida() {
        servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                .willReturn(aResponse().withStatus(200)
                        .withFixedDelay(50)
                        .withBody(pagina("12.2.6"))));

        EcfLatestVersionService servico =
                new EcfLatestVersionService(cliente, new EcfPageParser(), Duration.ofSeconds(5));

        assertThat(servico.getLatestVersion(urlBase, CLASSE)).isEqualTo("12.2.6");
    }

    @Test
    @DisplayName("Porta sem servidor (conexao recusada) retorna null")
    void conexaoRecusadaRetornaNull() {
        // Porta 1 nao tem listener: ConnectException.
        String urlMorta = "http://localhost:1/ecf";

        assertThat(servico().getLatestVersion(urlMorta, CLASSE)).isNull();
    }

    @Test
    @DisplayName("Host inexistente retorna null")
    void hostInexistenteRetornaNull() {
        assertThat(servico().getLatestVersion(
                "https://host-que-nao-existe.invalid/ecf", CLASSE)).isNull();
    }

    @ParameterizedTest(name = "[{index}] URL malformada")
    @ValueSource(strings = {"nao-e-url", "ht!tp://errado", "://sem-esquema"})
    @DisplayName("URL sintaticamente invalida retorna null")
    void urlMalformadaRetornaNull(String url) {
        assertThat(servico().getLatestVersion(url, CLASSE)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("URL em branco retorna null sem tentar conexao")
    void urlEmBrancoRetornaNull(String url) {
        assertThat(servico().getLatestVersion(url, CLASSE)).isNull();
    }

    // ===================== Requisicao enviada =====================

    @Test
    @DisplayName("Envia User-Agent de navegador (o portal recusa clientes genericos)")
    void enviaUserAgentDeNavegador() {
        servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                .willReturn(aResponse().withStatus(200).withBody(pagina("12.2.6"))));

        servico().getLatestVersion(urlBase, CLASSE);

        servidor.verify(getRequestedFor(urlPathEqualTo(CAMINHO))
                .withHeader("User-Agent", com.github.tomakehurst.wiremock.client.WireMock
                        .containing("Mozilla")));
    }

    @Test
    @DisplayName("O metodo usado e GET, uma vez por verificacao")
    void metodoGetUmaVez() {
        servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                .willReturn(aResponse().withStatus(200).withBody(pagina("12.2.6"))));

        servico().getLatestVersion(urlBase, CLASSE);

        servidor.verify(1, getRequestedFor(urlPathEqualTo(CAMINHO)));
    }

    @Test
    @DisplayName("Falha no primeiro acesso e sucesso no seguinte nao deixa estado sujo")
    void recuperacaoAposFalha() {
        servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                .inScenario("recuperacao")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("ok"));
        servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                .inScenario("recuperacao")
                .whenScenarioStateIs("ok")
                .willReturn(aResponse().withStatus(200).withBody(pagina("12.2.6"))));

        EcfLatestVersionService servico = servico();
        assertThat(servico.getLatestVersion(urlBase, CLASSE)).isNull();
        assertThat(servico.getLatestVersion(urlBase, CLASSE)).isEqualTo("12.2.6");
    }

    // ===================== Agendador sobrevive a falhas =====================

    @Nested
    @DisplayName("Agendador durante falhas de rede")
    class AgendadorResiliente {

        @Test
        @DisplayName("O agendador continua executando apos falhas consecutivas")
        void agendadorContinuaAposFalhas() throws InterruptedException {
            servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                    .willReturn(aResponse().withStatus(503)));

            AtomicInteger execucoes = new AtomicInteger();
            CountDownLatch cincoExecucoes = new CountDownLatch(5);
            List<Throwable> erros = new ArrayList<>();

            // Este e o mesmo tipo de executor usado pelo App para as verificacoes.
            ScheduledExecutorService agendador = Executors.newSingleThreadScheduledExecutor();
            try {
                agendador.scheduleAtFixedRate(() -> {
                    try {
                        // O UpdateService nunca lanca por falha de rede; aqui
                        // chamamos o servico direto para provar que o proprio
                        // servico tambem nao lanca.
                        servico().getLatestVersion(urlBase, CLASSE);
                        execucoes.incrementAndGet();
                        cincoExecucoes.countDown();
                    } catch (Throwable t) {
                        erros.add(t);
                        cincoExecucoes.countDown();
                    }
                }, 0, 50, TimeUnit.MILLISECONDS);

                boolean concluiu = cincoExecucoes.await(10, TimeUnit.SECONDS);

                assertThat(concluiu).as("O agendador deveria ter executado 5 vezes").isTrue();
                assertThat(erros).as("Nenhuma excecao pode escapar para o agendador").isEmpty();
                assertThat(execucoes.get()).isGreaterThanOrEqualTo(5);
            } finally {
                agendador.shutdownNow();
                assertThat(agendador.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }

        @Test
        @DisplayName("Alternar entre erro e sucesso nao interrompe o agendamento")
        void alternanciaErroSucesso() throws InterruptedException {
            servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                    .inScenario("alternado")
                    .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                    .willReturn(aResponse().withStatus(200).withBody(pagina("12.2.6")))
                    .willSetStateTo("falha"));
            servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                    .inScenario("alternado")
                    .whenScenarioStateIs("falha")
                    .willReturn(aResponse().withStatus(500))
                    .willSetStateTo(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED));

            List<String> resultados = java.util.Collections.synchronizedList(new ArrayList<>());
            CountDownLatch quatro = new CountDownLatch(4);
            ScheduledExecutorService agendador = Executors.newSingleThreadScheduledExecutor();
            try {
                agendador.scheduleAtFixedRate(() -> {
                    resultados.add(String.valueOf(servico().getLatestVersion(urlBase, CLASSE)));
                    quatro.countDown();
                }, 0, 50, TimeUnit.MILLISECONDS);

                assertThat(quatro.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                agendador.shutdownNow();
                agendador.awaitTermination(5, TimeUnit.SECONDS);
            }

            assertThat(resultados).contains("12.2.6").contains("null");
        }

        @Test
        @DisplayName("Um agendador nao pode ser bloqueado por um servidor lento")
        void servidorLentoNaoTravaOAgendador() throws InterruptedException {
            servidor.stubFor(get(urlPathEqualTo(CAMINHO))
                    .willReturn(aResponse().withStatus(200).withFixedDelay(300)
                            .withBody(pagina("12.2.6"))));

            HttpClient clienteCurto = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(1))
                    .build();
            CountDownLatch tres = new CountDownLatch(3);
            long inicio = System.nanoTime();

            ScheduledExecutorService agendador = Executors.newSingleThreadScheduledExecutor();
            try {
                agendador.scheduleAtFixedRate(() -> {
                    new EcfLatestVersionService(clienteCurto).getLatestVersion(urlBase, CLASSE);
                    tres.countDown();
                }, 0, 50, TimeUnit.MILLISECONDS);

                assertThat(tres.await(15, TimeUnit.SECONDS)).isTrue();
            } finally {
                agendador.shutdownNow();
                agendador.awaitTermination(5, TimeUnit.SECONDS);
            }

            long decorridoMs = (System.nanoTime() - inicio) / 1_000_000;
            // Tres execucoes com 300ms de latencia cada cabem folgadamente em 15s;
            // o que o teste garante e que o servico sempre retorna.
            assertThat(decorridoMs).isLessThan(15_000);
        }
    }
}

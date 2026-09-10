package app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Descobre a ultima versao da ECF publicada no site da Receita Federal.
 *
 * <p>O acesso HTTP fica isolado em {@link #getLatestVersion}; o parsing do HTML
 * e delegado ao {@link EcfPageParser}. Cliente e timeout sao injetaveis para
 * permitir testes com servidor local.</p>
 */
public class EcfLatestVersionService {

    private static final Logger log = LoggerFactory.getLogger(EcfLatestVersionService.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    /** O cliente HTTP limita so a conexao; este e o limite da requisicao toda. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final EcfPageParser parser;
    private final Duration requestTimeout;

    public EcfLatestVersionService() {
        this(HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    /** Construtor para testes e reuso de conexao. */
    public EcfLatestVersionService(HttpClient httpClient) {
        this(httpClient, new EcfPageParser(), REQUEST_TIMEOUT);
    }

    public EcfLatestVersionService(HttpClient httpClient, EcfPageParser parser) {
        this(httpClient, parser, REQUEST_TIMEOUT);
    }

    /**
     * @param requestTimeout tempo maximo da requisicao; parametrizavel para os
     *                       testes exercitarem servidor lento sem esperar 30s.
     */
    public EcfLatestVersionService(HttpClient httpClient, EcfPageParser parser,
                                   Duration requestTimeout) {
        this.httpClient = httpClient;
        this.parser = parser;
        this.requestTimeout = requestTimeout;
    }

    /**
     * Baixa a pagina e extrai a versao publicada.
     *
     * @return a versao, ou {@code null} se o download ou a extracao falharem.
     */
    public String getLatestVersion(String url, String className) {
        String html = fetchHtml(url);
        if (html == null) {
            return null;
        }
        return parser.extractVersion(html, className);
    }

    /** Extrai a versao de um HTML ja obtido. */
    public String extractLatestVersion(String html, String className) {
        return parser.extractVersion(html, className);
    }

    /** Extrai o link de download oficial publicado na pagina. */
    public String extractDownloadUrl(String html) {
        return parser.extractDownloadUrl(html);
    }

    /**
     * Baixa o corpo da pagina. Devolve {@code null} em vez de lancar para
     * qualquer falha: URL malformada, status diferente de 200, timeout, queda de
     * conexao, handshake TLS invalido ou thread interrompida.
     */
    public String fetchHtml(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .timeout(requestTimeout)
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Falha ao acessar a URL. Status Code: {}", response.statusCode());
                return null;
            }

            String corpo = response.body();
            if (corpo == null || corpo.isBlank()) {
                log.warn("A pagina respondeu 200, porem sem conteudo.");
                return null;
            }
            return corpo;
        } catch (IOException e) {
            // Cobre UnknownHost, ConnectException, SSLHandshake e timeout.
            log.warn("Erro ao acessar o site: {}", e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Requisicao interrompida ao acessar o site: {}", e.getMessage());
            return null;
        } catch (RuntimeException e) {
            // URI.create lanca excecao para URL malformada.
            log.warn("URL invalida '{}': {}", url, e.getMessage());
            return null;
        }
    }
}

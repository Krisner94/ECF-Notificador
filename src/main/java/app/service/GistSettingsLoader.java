package app.service;

import app.config.AppConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Baixa o arquivo de configuracao de um Gist publico do GitHub.
 *
 * <p>O Gist e a fonte de verdade compartilhada: alterando o conteudo la, todas as
 * instalacoes usam a nova configuracao sem reinstalar o programa.</p>
 *
 * <p>O download e sempre tolerante a falha. Sem internet, com o Gist fora do ar
 * ou com conteudo invalido, {@link #load()} devolve {@code null} e quem chamou
 * usa o ultimo valor conhecido (cache local) - a rede nunca impede o programa de
 * iniciar.</p>
 */
public class GistSettingsLoader {

    private static final Logger log = LoggerFactory.getLogger(GistSettingsLoader.class);

    /**
     * URL "raw" do Gist. Sobrescrevivel com -Dapp.config.gistUrl=... para
     * apontar outro Gist sem recompilar.
     */
    private static final String DEFAULT_GIST_URL =
            "https://gist.githubusercontent.com/Krisner94/e2e1820cb36f7bee41a5212fe9dbad35/raw/appSettings.json";

    private static final String GIST_URL_PROPERTY = "app.config.gistUrl";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private static final String USER_AGENT = "ECF-Notificador";

    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String gistUrl;
    private final HttpClient httpClient;

    public GistSettingsLoader() {
        this(resolveGistUrl());
    }

    public GistSettingsLoader(String gistUrl) {
        this.gistUrl = gistUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Le a configuracao do Gist.
     *
     * @return a configuracao lida, ou {@code null} se o download ou a leitura
     *         falharem por qualquer motivo.
     */
    public AppConfig load() {
        String json = download();
        if (json == null || json.isBlank()) {
            return null;
        }
        return parse(json);
    }

    /** URL efetivamente usada, util para log e diagnostico. */
    public String getGistUrl() {
        return gistUrl;
    }

    private String download() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gistUrl))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Gist respondeu com status {}. Usando configuracao local.", response.statusCode());
                return null;
            }
            return response.body();
        } catch (IOException e) {
            log.warn("Falha de rede ao ler o Gist ({}). Usando configuracao local.", e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Leitura do Gist interrompida. Usando configuracao local.");
            return null;
        } catch (RuntimeException e) {
            log.warn("URL do Gist invalida ({}). Usando configuracao local.", e.getMessage());
            return null;
        }
    }

    /**
     * Converte o JSON do Gist em configuracao.
     *
     * <p>Metodo de pacote (visivel para os testes) e sem acesso a rede, para
     * que o parsing possa ser coberto sem depender de internet.</p>
     *
     * @return a configuracao desserializada ou {@code null} se o JSON for invalido.
     */
    static AppConfig parse(String json) {
        if (json == null || json.isBlank()) {
            log.warn("Conteudo do Gist vazio. Usando configuracao local.");
            return null;
        }
        try {
            AppConfig config = mapper.readValue(json, AppConfig.class);
            log.debug("Configuracao carregada do Gist com sucesso.");
            return config;
        } catch (IOException e) {
            log.warn("Conteudo do Gist nao e um JSON valido ({}). "
                    + "Usando configuracao local.", e.getMessage());
            return null;
        }
    }

    private static String resolveGistUrl() {
        String configured = System.getProperty(GIST_URL_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_GIST_URL;
        }
        return configured;
    }
}

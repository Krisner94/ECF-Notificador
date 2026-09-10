package app.service;

import app.config.AppConfig;
import app.config.EcfConfig;
import app.config.SettingsData;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private static final String CONFIG_DIRECTORY = "config";
    private static final String CONFIG_FILE = "appSettings.json";
    private static final int DEFAULT_CHECK_INTERVAL_HOURS = 6;
    private static final String DEFAULT_ICON_PATH = "EcfNotificador.ico";
    private static final String DEFAULT_VERSION_HTML_CLASS = "rfb_subheader";

    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path settingsPath;
    private final GistSettingsLoader gistLoader;
    private AppConfig appConfig;

    public SettingsService() {
        this(new GistSettingsLoader(), defaultSettingsPath());
    }

    public SettingsService(GistSettingsLoader gistLoader) {
        this(gistLoader, defaultSettingsPath());
    }

    /**
     * Construtor com caminho explicito do arquivo de configuracao.
     *
     * <p>Existe para os testes poderem trabalhar em um diretorio temporario sem
     * tocar no arquivo real do usuario (o padrao e {@code config/appSettings.json}
     * relativo ao diretorio de execucao).</p>
     *
     * @param gistLoader   fonte remota da configuracao (pode ser um stub).
     * @param settingsPath arquivo local usado como cache.
     */
    public SettingsService(GistSettingsLoader gistLoader, Path settingsPath) {
        this.gistLoader = gistLoader;
        this.settingsPath = settingsPath;
        loadSettings();
        refreshFromGist();
    }

    /** Arquivo de configuracao padrao: {@code config/appSettings.json} ao lado do programa. */
    private static Path defaultSettingsPath() {
        return Paths.get(System.getProperty("user.dir"), CONFIG_DIRECTORY, CONFIG_FILE);
    }

    /** Caminho do arquivo local de configuracao, util para log e diagnostico. */
    public Path getSettingsPath() {
        return settingsPath;
    }

    /**
     * Baixa a configuracao do Gist e atualiza o cache local.
     *
     * <p>Chamado na inicializacao. Se o Gist estiver inacessivel, mantem a
     * configuracao local - o programa continua funcionando offline.</p>
     */
    public void refreshFromGist() {
        AppConfig baixada = gistLoader.load();
        if (baixada == null) {
            log.info("Mantendo a configuracao local (Gist indisponivel).");
            return;
        }

        mergeGistIntoLocal(baixada);

        // Guarda o que veio do Gist para uso offline na proxima vez.
        saveSettings();
    }

    public int getCheckIntervalHours() {
        SettingsData settings = getSettings();
        Integer interval = settings.getCheckIntervalHours();
        if (interval == null || interval < 1) {
            return DEFAULT_CHECK_INTERVAL_HOURS;
        }
        return interval;
    }

    /**
     * Registra a escolha do usuario e a marca como prioritaria sobre o Gist.
     */
    public void setCheckIntervalHours(int value) {
        SettingsData settings = getSettings();
        settings.setCheckIntervalHours(value);
        settings.setIntervalOverriddenByUser(true);
        saveSettings();
    }

    public String getEcfInstallPath() {
        EcfConfig ecf = getEcf();
        if (ecf.getInstallPath() == null) {
            return "";
        }
        return ecf.getInstallPath();
    }

    public String getDownloadUrl() {
        EcfConfig ecf = getEcf();
        if (ecf.getDownloadUrl() == null) {
            return "";
        }
        return ecf.getDownloadUrl();
    }

    public String getVersionHtmlClass() {
        EcfConfig ecf = getEcf();
        String htmlClass = ecf.getVersionHtmlClass();
        if (htmlClass == null || htmlClass.isBlank()) {
            return DEFAULT_VERSION_HTML_CLASS;
        }
        return htmlClass;
    }

    public String getIconPath() {
        SettingsData settings = getSettings();
        String iconPath = settings.getIconPath();
        if (iconPath == null || iconPath.isBlank()) {
            return DEFAULT_ICON_PATH;
        }
        return iconPath;
    }

    /**
     * Aplica os valores do Gist sobre a configuracao local.
     *
     * <p>Excecao: o intervalo de verificacao so e sobrescrito se o usuario ainda
     * nao tiver escolhido um valor proprio pela tela de Configuracoes.</p>
     */
    private void mergeGistIntoLocal(AppConfig origem) {
        if (origem.getEcf() != null) {
            getAppConfig().setEcf(origem.getEcf());
        }

        SettingsData origemSettings = origem.getSettings();
        if (origemSettings == null) {
            return;
        }

        SettingsData destino = getSettings();
        if (!destino.isIntervalOverriddenByUser()) {
            destino.setCheckIntervalHours(origemSettings.getCheckIntervalHours());
        }
        if (origemSettings.getIconPath() != null && !origemSettings.getIconPath().isBlank()) {
            destino.setIconPath(origemSettings.getIconPath());
        }
    }

    private AppConfig getAppConfig() {
        if (appConfig == null) {
            appConfig = new AppConfig();
        }
        return appConfig;
    }

    private EcfConfig getEcf() {
        AppConfig config = getAppConfig();
        if (config.getEcf() == null) {
            config.setEcf(new EcfConfig());
        }
        return config.getEcf();
    }

    private SettingsData getSettings() {
        AppConfig config = getAppConfig();
        if (config.getSettings() == null) {
            config.setSettings(new SettingsData());
        }
        return config.getSettings();
    }

    private void loadSettings() {
        if (!Files.exists(settingsPath)) {
            appConfig = new AppConfig();
            return;
        }
        try {
            appConfig = mapper.readValue(settingsPath.toFile(), AppConfig.class);
        } catch (IOException e) {
            log.warn("Falha ao ler o arquivo de configuracao '{}': {}", settingsPath, e.getMessage());
            appConfig = new AppConfig();
        }
    }

    private void saveSettings() {
        try {
            Path parent = settingsPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            mapper.writeValue(settingsPath.toFile(), getAppConfig());
        } catch (IOException e) {
            log.error("Erro ao salvar o arquivo de configuracao: {}", e.getMessage());
        }
    }
}

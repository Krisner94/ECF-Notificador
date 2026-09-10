package app.testutil;

import app.config.AppConfig;
import app.config.EcfConfig;
import app.config.SettingsData;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Monta configuracoes para os testes, com a mesma tolerancia do app real. */
public final class TestConfigs {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private TestConfigs() {
    }

    /** Desserializa o JSON usando as mesmas configuracoes do aplicativo. */
    public static AppConfig fromJson(String json) {
        try {
            return MAPPER.readValue(json, AppConfig.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON de teste invalido: " + e.getMessage(), e);
        }
    }

    /** Cria uma configuracao completa sem passar por JSON. */
    public static AppConfig build(String installPath, String downloadUrl,
                                  String versionHtmlClass, Integer intervalHours, String iconPath) {
        EcfConfig ecf = new EcfConfig();
        ecf.setInstallPath(installPath);
        ecf.setDownloadUrl(downloadUrl);
        ecf.setVersionHtmlClass(versionHtmlClass);

        SettingsData settings = new SettingsData();
        settings.setCheckIntervalHours(intervalHours);
        settings.setIconPath(iconPath);

        AppConfig config = new AppConfig();
        config.setEcf(ecf);
        config.setSettings(settings);
        return config;
    }
}

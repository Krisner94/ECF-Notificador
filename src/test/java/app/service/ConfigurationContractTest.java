package app.service;

import app.config.AppConfig;
import app.config.EcfConfig;
import app.config.SettingsData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Contratos externos do JSON publicado no Gist e persistido no cache. */
class ConfigurationContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("aceita o contrato JSON publicado pelo Gist")
    void aceitaContratoPublicado() throws Exception {
        String json = """
                {
                  "Ecf": {
                    "InstallPath": "C:\\\\SpedECF\\\\response.varfile",
                    "DownloadUrl": "https://example.test/ecf.html",
                    "VersionHtmlClass": "rfb_subheader"
                  },
                  "Settings": {
                    "CheckIntervalHours": 6,
                    "IconPath": "EcfNotificador.ico"
                  }
                }
                """;

        AppConfig config = GistSettingsLoader.parse(json);

        assertThat(config).isNotNull();
        assertThat(config.getEcf().getInstallPath()).isEqualTo("C:\\SpedECF\\response.varfile");
        assertThat(config.getEcf().getDownloadUrl()).isEqualTo("https://example.test/ecf.html");
        assertThat(config.getEcf().getVersionHtmlClass()).isEqualTo("rfb_subheader");
        assertThat(config.getSettings().getCheckIntervalHours()).isEqualTo(6);
        assertThat(config.getSettings().getIconPath()).isEqualTo("EcfNotificador.ico");
    }

    @Test
    @DisplayName("tolera campos novos sem quebrar clientes antigos")
    void ignoraCamposDesconhecidos() {
        AppConfig config = GistSettingsLoader.parse("""
                {
                  "Ecf": { "DownloadUrl": "https://example.test/ecf.html", "FutureField": true },
                  "Settings": { "CheckIntervalHours": 4, "FutureSetting": "x" },
                  "FutureRoot": 42
                }
                """);

        assertThat(config).isNotNull();
        assertThat(config.getEcf().getDownloadUrl()).isEqualTo("https://example.test/ecf.html");
        assertThat(config.getSettings().getCheckIntervalHours()).isEqualTo(4);
    }

    @Test
    @DisplayName("campos ausentes permanecem ausentes para permitir merge parcial")
    void preservaSemanticaDeCamposAusentes() {
        AppConfig config = GistSettingsLoader.parse(""
                + "{\"Ecf\":{\"DownloadUrl\":\"https://example.test/ecf.html\"},"
                + "\"Settings\":{}} ");

        assertThat(config).isNotNull();
        assertThat(config.getEcf().getInstallPath()).isNull();
        assertThat(config.getEcf().getVersionHtmlClass()).isNull();
        assertThat(config.getSettings().getCheckIntervalHours()).isNull();
        assertThat(config.getSettings().getIconPath()).isNull();
    }

    @Test
    @DisplayName("cache serializado usa os nomes públicos do contrato")
    void serializaNomesDoContrato() throws Exception {
        AppConfig config = new AppConfig();
        EcfConfig ecf = new EcfConfig();
        ecf.setDownloadUrl("https://example.test/ecf.html");
        SettingsData settings = new SettingsData();
        settings.setCheckIntervalHours(6);
        config.setEcf(ecf);
        config.setSettings(settings);

        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(config));

        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder("Ecf", "Settings");
        assertThat(json.path("Ecf").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("DownloadUrl");
        assertThat(json.path("Settings").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("CheckIntervalHours", "IntervalOverriddenByUser");
    }
}

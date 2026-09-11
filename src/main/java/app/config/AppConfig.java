package app.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppConfig {

    @JsonProperty("Ecf")
    private EcfConfig ecf = new EcfConfig();

    @JsonProperty("Settings")
    private SettingsData settings = new SettingsData();

    public EcfConfig getEcf() {
        return ecf;
    }

    public void setEcf(EcfConfig ecf) {
        this.ecf = ecf;
    }

    public SettingsData getSettings() {
        return settings;
    }

    public void setSettings(SettingsData settings) {
        this.settings = settings;
    }
}

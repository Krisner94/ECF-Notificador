package app.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SettingsData {

    @JsonProperty("CheckIntervalHours")
    private Integer checkIntervalHours = 6;

    @JsonProperty("IconPath")
    private String iconPath = "EcfNotificador.ico";

    /**
     * Marca que o usuario alterou o intervalo pela tela de Configuracoes.
     * Enquanto for {@code false}, o valor vem do Gist. Precisa ser gravado no
     * arquivo local, senao a escolha do usuario se perde a cada reinicio.
     */
    @JsonProperty("IntervalOverriddenByUser")
    private boolean intervalOverriddenByUser;

    public Integer getCheckIntervalHours() {
        return checkIntervalHours;
    }

    public void setCheckIntervalHours(Integer checkIntervalHours) {
        this.checkIntervalHours = checkIntervalHours;
    }

    public String getIconPath() {
        return iconPath;
    }

    public void setIconPath(String iconPath) {
        this.iconPath = iconPath;
    }

    public boolean isIntervalOverriddenByUser() {
        return intervalOverriddenByUser;
    }

    public void setIntervalOverriddenByUser(boolean intervalOverriddenByUser) {
        this.intervalOverriddenByUser = intervalOverriddenByUser;
    }
}

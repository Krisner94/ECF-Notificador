package app.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Dados da instalacao da ECF, vindos do Gist.
 *
 * <p>Os campos comecam nulos de proposito: {@code null} significa "esta chave
 * nao veio no JSON" e o valor atual e preservado no merge. Um {@code ""} como
 * padrao seria indistinguivel de "veio vazio" e apagaria a configuracao boa.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EcfConfig {

    @JsonProperty("InstallPath")
    private String installPath;

    @JsonProperty("DownloadUrl")
    private String downloadUrl;

    @JsonProperty("VersionHtmlClass")
    private String versionHtmlClass;

    public String getInstallPath() {
        return installPath;
    }

    public void setInstallPath(String installPath) {
        this.installPath = installPath;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getVersionHtmlClass() {
        return versionHtmlClass;
    }

    public void setVersionHtmlClass(String versionHtmlClass) {
        this.versionHtmlClass = versionHtmlClass;
    }
}

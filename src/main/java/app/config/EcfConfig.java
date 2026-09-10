package app.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EcfConfig {

    @JsonProperty("InstallPath")
    private String installPath = "";

    @JsonProperty("DownloadUrl")
    private String downloadUrl = "";

    @JsonProperty("VersionHtmlClass")
    private String versionHtmlClass = "rfb_subheader";

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

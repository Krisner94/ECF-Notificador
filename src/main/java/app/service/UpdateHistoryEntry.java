package app.service;

/**
 * Um registro do historico de verificacoes.
 *
 * @param timestamp data/hora da verificacao, em formato ISO.
 * @param installedVersion versao instalada (vazia quando indisponivel).
 * @param latestVersion versao publicada (vazia quando indisponivel).
 * @param result resultado da comparacao.
 */
public record UpdateHistoryEntry(String timestamp,
                                 String installedVersion,
                                 String latestVersion,
                                 UpdateService.CheckResult result) {

    /** @return {@code true} quando havia as duas versoes para comparar. */
    public boolean comparou() {
        return !installedVersion.isEmpty() && !latestVersion.isEmpty();
    }

    /** @return {@code true} quando o usuario foi avisado de uma versao nova. */
    public boolean avisouUsuario() {
        return result == UpdateService.CheckResult.UPDATE_AVAILABLE;
    }
}

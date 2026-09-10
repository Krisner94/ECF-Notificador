package app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EcfVersionService {

    private static final Logger log = LoggerFactory.getLogger(EcfVersionService.class);

    /** Primeiro numero no formato X.Y ou X.Y.Z encontrado no texto. */
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)");

    /**
     * Le a versao instalada da ECF a partir do arquivo de instalacao.
     *
     * @param path caminho do arquivo (normalmente o {@code response.varfile}).
     * @return a versao encontrada ou {@code null} quando o arquivo nao existe,
     *         nao pode ser lido ou nao contem numero de versao.
     */
    public String getInstalledVersion(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        Path filePath;
        try {
            filePath = Paths.get(path);
        } catch (RuntimeException e) {
            log.warn("Caminho de instalacao invalido '{}': {}", path, e.getMessage());
            return null;
        }

        if (!Files.exists(filePath)) {
            return null;
        }

        String content = readFile(filePath);
        if (content == null) {
            return null;
        }

        return extractVersion(content);
    }

    /**
     * Extrai a primeira versao no formato {@code X.Y} ou {@code X.Y.Z} do texto.
     *
     * <p>Metodo puro, sem acesso a disco: concentra a regra de parsing testada.
     * A busca cobre todo o texto (nao apenas a primeira linha) porque o arquivo
     * de instalacao cita a versao no cabecalho do comentario.</p>
     *
     * @return a versao encontrada ou {@code null} se o texto for nulo/vazio ou
     *         nao contiver nenhum numero de versao.
     */
    public String extractVersion(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = VERSION_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String readFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Falha ao ler o arquivo de versao '{}': {}", path, e.getMessage());
            return null;
        } catch (RuntimeException e) {
            log.warn("Arquivo de versao invalido '{}': {}", path, e.getMessage());
            return null;
        }
    }
}

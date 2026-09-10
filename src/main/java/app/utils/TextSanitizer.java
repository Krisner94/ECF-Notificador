package app.utils;

import java.util.regex.Pattern;

/**
 * Valida e sanitiza os textos que vem do site da Receita Federal antes de
 * serem exibidos na notificacao nativa do Windows.
 *
 * <p>O conteudo da pagina e fonte externa e chega a API Win32 como string larga.
 * Caracteres de controle, quebra de linha ou tamanho ilimitado podem quebrar o
 * layout do popup, poluir os logs ou desperdicar memoria.</p>
 *
 * <p>Concentra tambem a validacao de URL: so HTTP/HTTPS pode ser aberto no
 * navegador, impedindo que uma configuracao remota use um esquema perigoso.</p>
 */
public final class TextSanitizer {

    /** Tamanho maximo do texto exibido no popup, em caracteres. */
    public static final int MAX_TEXT_LENGTH = 200;

    /** Tamanho maximo tolerado para uma versao (ex.: "12.2.6-rc1"). */
    public static final int MAX_VERSION_LENGTH = 32;

    /** Assinatura minima de uma versao valida: {@code X.Y}. */
    private static final int MIN_VERSION_LENGTH = 3;

    /**
     * Formato aceito: {@code X(.Y)+} com sufixo opcional de release.
     * O digito obrigatorio dos dois lados do ponto rejeita {@code 12..6}.
     */
    private static final Pattern VERSION_SHAPE =
            Pattern.compile("^\\d+(\\.\\d+){1,3}(-[A-Za-z0-9.]+)?$");

    private static final String ELLIPSIS = "...";

    private TextSanitizer() {
    }

    /**
     * Normaliza um texto de exibicao: remove caracteres de controle, colapsa
     * espacos, apara as pontas e trunca em {@link #MAX_TEXT_LENGTH}.
     *
     * @return texto seguro para exibicao; nunca nulo.
     */
    public static String sanitizeDisplayText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        String semControle = removeControlCharacters(value);
        String espacosNormalizados = semControle.replaceAll("\\s+", " ").trim();
        return truncate(espacosNormalizados, MAX_TEXT_LENGTH);
    }

    /**
     * Valida uma versao extraida do site.
     *
     * @return a versao apara da quando valida; {@code null} caso contrario.
     */
    public static String sanitizeVersion(String version) {
        if (version == null) {
            return null;
        }

        String trimmed = removeControlCharacters(version).trim();

        if (trimmed.length() < MIN_VERSION_LENGTH || trimmed.length() > MAX_VERSION_LENGTH) {
            return null;
        }
        if (!VERSION_SHAPE.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed;
    }

    /** Aceita apenas {@code http} e {@code https} com host nao vazio. */
    public static boolean isSafeHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        String trimmed = stripControlCharacters(url).trim();
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);

        if (lower.startsWith("https://")) {
            return hasHost(trimmed.substring("https://".length()));
        }
        if (lower.startsWith("http://")) {
            return hasHost(trimmed.substring("http://".length()));
        }
        return false;
    }

    /** Valida a URL e devolve a versao apara da, ou {@code null} se insegura. */
    public static String sanitizeUrlOrNull(String url) {
        if (!isSafeHttpUrl(url)) {
            return null;
        }
        return stripControlCharacters(url).trim();
    }

    /**
     * Remove caracteres de controle <b>sem</b> substituto.
     *
     * <p>Em URLs, trocar um controle por espaco produziria uma URL invalida.
     * Remover tambem impede que {@code java\nscript:} escape da checagem de
     * esquema.</p>
     */
    private static String stripControlCharacters(String value) {
        StringBuilder resultado = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char caractere = value.charAt(i);
            if (!Character.isISOControl(caractere)) {
                resultado.append(caractere);
            }
        }
        return resultado.toString();
    }

    /** Remove caracteres de controle, preservando acentos e texto normal. */
    private static String removeControlCharacters(String value) {
        StringBuilder resultado = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char caractere = value.charAt(i);
            if (!Character.isISOControl(caractere)) {
                resultado.append(caractere);
            } else if (caractere == '\t' || caractere == '\n' || caractere == '\r') {
                // Espaco no lugar da quebra: evita colar duas palavras.
                resultado.append(' ');
            }
        }
        return resultado.toString();
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - ELLIPSIS.length()) + ELLIPSIS;
    }

    private static boolean hasHost(String remainder) {
        int fimDoHost = remainder.length();
        for (int i = 0; i < remainder.length(); i++) {
            char caractere = remainder.charAt(i);
            if (caractere == '/' || caractere == '?' || caractere == '#') {
                fimDoHost = i;
                break;
            }
        }
        String host = remainder.substring(0, fimDoHost);
        return !host.isBlank() && !host.startsWith(":");
    }
}

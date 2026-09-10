package app.service;

import app.utils.TextSanitizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a versao publicada e o link de download da pagina da Receita Federal.
 *
 * <p>Usa JSoup (DOM real) em vez de regex sobre o HTML cru: o portal repete a
 * mesma classe CSS em varios blocos, e um regex casaria o primeiro numero da
 * pagina - que pode estar em outro bloco - anunciando a versao errada.</p>
 *
 * <p>Todo texto obtido do DOM passa por {@link TextSanitizer} antes de sair.</p>
 */
public class EcfPageParser {

    private static final Logger log = LoggerFactory.getLogger(EcfPageParser.class);

    /** Versao X.Y.Z, com sufixo de release opcional (12.2.6-rc1). */
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("(\\d+\\.\\d+\\.\\d+(?:-[A-Za-z0-9.]+)?)");

    private static final int MAX_SEARCH_DEPTH = 3;

    /**
     * @param className classe CSS que marca o bloco da versao.
     * @return a versao validada, ou {@code null} se nao for encontrada.
     */
    public String extractVersion(String html, String className) {
        Document document = parse(html);
        if (document == null || className == null || className.isBlank()) {
            return null;
        }

        Elements elementos = document.getElementsByClass(className);
        if (elementos.isEmpty()) {
            log.warn("Nenhum elemento com a classe '{}' foi encontrado na pagina.", className);
            return null;
        }

        for (Element elemento : elementos) {
            String versao = buscarVersaoNoElemento(elemento);
            if (versao != null) {
                return versao;
            }
        }

        log.warn("Bloco '{}' encontrado, mas sem numero de versao valido.", className);
        return null;
    }

    /** Extrai o link de download oficial; aceita apenas HTTP/HTTPS. */
    public String extractDownloadUrl(String html) {
        Document document = parse(html);
        if (document == null) {
            return null;
        }

        for (Element link : document.select("a[href]")) {
            String absoluto = link.absUrl("href");
            String candidato = absoluto.isBlank() ? link.attr("href") : absoluto;
            String seguro = TextSanitizer.sanitizeUrlOrNull(candidato);
            if (seguro != null && pareceLinkDeDownload(link, seguro)) {
                return seguro;
            }
        }
        return null;
    }

    private boolean pareceLinkDeDownload(Element link, String url) {
        String texto = link.text().toLowerCase(java.util.Locale.ROOT);
        String href = url.toLowerCase(java.util.Locale.ROOT);
        return texto.contains("download")
                || texto.contains("instalador")
                || href.contains("download")
                || href.contains("sped");
    }

    /** Busca a versao no elemento e, se preciso, nos filhos. */
    private String buscarVersaoNoElemento(Element raiz) {
        String direto = extrairVersaoDoTexto(raiz.ownText());
        if (direto != null) {
            return direto;
        }

        String textoCompleto = extrairVersaoDoTexto(raiz.text());
        if (textoCompleto != null) {
            return textoCompleto;
        }

        return buscarEmFilhos(raiz, 0);
    }

    private String buscarEmFilhos(Element elemento, int profundidade) {
        if (profundidade >= MAX_SEARCH_DEPTH) {
            return null;
        }
        for (Element filho : elemento.children()) {
            String versao = extrairVersaoDoTexto(filho.text());
            if (versao != null) {
                return versao;
            }
            String recursivo = buscarEmFilhos(filho, profundidade + 1);
            if (recursivo != null) {
                return recursivo;
            }
        }
        return null;
    }

    /** Aplica o regex e valida o resultado. */
    private String extrairVersaoDoTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        Matcher matcher = VERSION_PATTERN.matcher(texto);
        if (!matcher.find()) {
            return null;
        }
        return TextSanitizer.sanitizeVersion(matcher.group(1));
    }

    /** Parsing tolerante; devolve null em vez de lancar. */
    private Document parse(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        try {
            return Jsoup.parse(html);
        } catch (RuntimeException e) {
            log.warn("HTML da pagina nao pode ser interpretado: {}", e.getMessage());
            return null;
        }
    }
}

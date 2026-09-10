package app.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Testes da leitura da ultima versao publicada pela Receita Federal.
 *
 * <p>O parsing roda sobre HTML fixo, sem rede: um teste que baixasse a pagina
 * real ficaria lento e passaria a falhar sempre que o site mudasse. O metodo
 * {@code getLatestVersion} (que faz HTTP) e tolerante a falha e devolve
 * {@code null} quando a URL nao responde.</p>
 */
class EcfLatestVersionServiceTest {

    private static final String CLASSE_PADRAO = "rfb_subheader";

    private final EcfLatestVersionService service = new EcfLatestVersionService();

    // ===================== Parsing (sem rede) =====================

    @ParameterizedTest(name = "extrai ''{1}''")
    @MethodSource("htmlsValidos")
    @DisplayName("Extrai a versao marcada pela classe HTML")
    void extraiVersaoDoHtml(String html, String esperado) {
        assertEquals(esperado, service.extractLatestVersion(html, CLASSE_PADRAO));
    }

    private static Stream<Arguments> htmlsValidos() {
        return Stream.of(
                Arguments.of("<h3 class=\"rfb_subheader\">12.2.6</h3>", "12.2.6"),
                // Espacos em volta do numero (trim aplicado).
                Arguments.of("<h3 class=\"rfb_subheader\"> 12.2.6 </h3>", "12.2.6"),
                // Numero dentro de elemento filho (caso real do portal).
                Arguments.of("<h3 class=\"rfb_subheader\"><span>12.2.6</span></h3>", "12.2.6"),
                // Atributos extras antes da classe.
                Arguments.of("<h3 id=\"titulo\" class=\"rfb_subheader\">12.2.6</h3>", "12.2.6"),
                // Quebras de linha entre a classe e o numero (DOTALL).
                Arguments.of("<h3 class=\"rfb_subheader\">\n   12.2.6\n</h3>", "12.2.6"),
                // Classe sem valor de atributo entre aspas.
                Arguments.of("<div class=rfb_subheader>12.2.6</div>", "12.2.6"),
                // Pagina grande com o alvo no meio.
                Arguments.of("<html><body>"
                        + "<div class=\"banner\">sped</div>"
                        + "<h3 class=\"rfb_subheader\">12.2.6</h3>"
                        + "<footer>rodape</footer></body></html>", "12.2.6")
        );
    }

    @ParameterizedTest(name = "html sem a classe: {0}")
    @ValueSource(strings = {
            "<h3 class=\"outra_classe\">12.2.6</h3>",
            "<h3>12.2.6</h3>",
            "<html><body>sem versao</body></html>"
    })
    @DisplayName("HTML sem a classe esperada retorna null")
    void htmlSemClasseRetornaNull(String html) {
        assertNull(service.extractLatestVersion(html, CLASSE_PADRAO));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("HTML nulo ou vazio retorna null")
    void htmlInvalidoRetornaNull(String html) {
        assertNull(service.extractLatestVersion(html, CLASSE_PADRAO));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("Classe HTML nula ou vazia retorna null")
    void classeInvalidaRetornaNull(String classe) {
        assertNull(service.extractLatestVersion(
                "<h3 class=\"rfb_subheader\">12.2.6</h3>", classe));
    }

    @Test
    @DisplayName("A classe e tratada como texto literal, nao como expressao regular")
    void classeNaoEhInterpretadaComoRegex() {
        // Pattern.quote impede que um ponto na classe vire "qualquer caractere".
        assertNull(service.extractLatestVersion(
                "<h3 class=\"rfbXsubheader\">12.2.6</h3>", "rfb.subheader"));
        assertEquals("12.2.6", service.extractLatestVersion(
                "<h3 class=\"rfb.subheader\">12.2.6</h3>", "rfb.subheader"));
    }

    @Test
    @DisplayName("Versao sem os tres componentes nao e reconhecida")
    void versaoIncompletaNaoEhReconhecida() {
        assertNull(service.extractLatestVersion(
                "<h3 class=\"rfb_subheader\">12.2</h3>", CLASSE_PADRAO));
    }

    // ===================== Tolerancia a falha de rede =====================

    @ParameterizedTest(name = "url sem resposta: {0}")
    @ValueSource(strings = {
            "nao-e-uma-url-valida",
            "https://host-que-nao-existe.invalid/appSettings.json",
            "http://localhost:1/pagina.html"
    })
    @DisplayName("URL invalida ou fora do ar retorna null, sem lancar excecao")
    void urlInvalidaRetornaNull(String url) {
        assertNull(service.getLatestVersion(url, CLASSE_PADRAO));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("URL nula ou vazia retorna null sem tentar conexao")
    void urlNulaRetornaNull(String url) {
        assertNull(service.getLatestVersion(url, CLASSE_PADRAO));
    }
}

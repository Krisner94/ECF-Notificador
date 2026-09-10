package app.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Modulo 1 - Parsing do HTML da Receita Federal (JSoup, em memoria).
 *
 * <p>Nenhum teste acessa a rede: o HTML de entrada e escrito aqui. O objetivo e
 * garantir que a extracao escolhe o elemento certo do DOM e nao "o primeiro
 * numero que aparecer", que era a fragilidade da versao com regex.</p>
 */
class EcfPageParserTest {

    private static final String CLASSE = "rfb_subheader";

    private final EcfPageParser parser = new EcfPageParser();

    @Nested
    @DisplayName("Extracao da versao")
    class ExtracaoDeVersao {

        @ParameterizedTest(name = "[{index}] esperado {1}")
        @MethodSource("htmlsValidos")
        @DisplayName("Extrai a versao do bloco marcado pela classe CSS")
        void extraiVersaoDoBloco(String html, String esperado) {
            assertThat(parser.extractVersion(html, CLASSE)).isEqualTo(esperado);
        }

        private static Stream<Arguments> htmlsValidos() {
            return Stream.of(
                    Arguments.of("<h3 class=\"rfb_subheader\">12.2.6</h3>", "12.2.6"),
                    Arguments.of("<h3 class=\"rfb_subheader\"> 12.2.6 </h3>", "12.2.6"),
                    // Versao em elemento filho (caso real do portal).
                    Arguments.of("<h3 class=\"rfb_subheader\"><span>12.2.6</span></h3>", "12.2.6"),
                    // Atributos extras antes da classe.
                    Arguments.of("<h3 id=\"titulo\" class=\"rfb_subheader\">12.2.6</h3>", "12.2.6"),
                    // Espaco entre a classe e o valor textual.
                    Arguments.of("<h3 class=\"rfb_subheader\">\n   12.2.6\n</h3>", "12.2.6"),
                    // O texto pode vir acompanhado de rotulo.
                    Arguments.of("<h3 class=\"rfb_subheader\">Vers\u00e3o 12.2.6</h3>", "12.2.6"),
                    // Sufixo de release.
                    Arguments.of("<h3 class=\"rfb_subheader\">12.2.6-rc1</h3>", "12.2.6-rc1"),
                    // Pagina inteira, com o alvo no meio.
                    Arguments.of(paginaCompleta("12.2.6"), "12.2.6")
            );
        }

        @Test
        @DisplayName("Ignora numero de versao que aparece FORA do bloco esperado")
        void ignoraNumeroForaDoBloco() {
            // Este e o bug que motivou a troca de regex por JSoup: o numero do
            // rodape nao pode "vencer" a versao real do bloco.
            String html = """
                    <html><body>
                      <footer>Nota fiscal 3.0 (schema 1.0)</footer>
                      <h3 class="rfb_subheader">12.2.6</h3>
                    </body></html>
                    """;

            assertThat(parser.extractVersion(html, CLASSE)).isEqualTo("12.2.6");
        }

        @Test
        @DisplayName("Escolhe o primeiro bloco que realmente contem uma versao")
        void escolhePrimeiroBlocoComVersao() {
            String html = """
                    <html><body>
                      <h3 class="rfb_subheader">Tabela de c\u00f3digos</h3>
                      <h3 class="rfb_subheader">12.2.6</h3>
                    </body></html>
                    """;

            assertThat(parser.extractVersion(html, CLASSE)).isEqualTo("12.2.6");
        }

        @ParameterizedTest(name = "[{index}] html sem a classe")
        @ValueSource(strings = {
                "<h3 class=\"outra_classe\">12.2.6</h3>",
                "<h3>12.2.6</h3>",
                "<html><body>sem versao</body></html>",
                "<h3 class=\"rfb_subheader\">Sem n\u00famero de vers\u00e3o</h3>",
                // Numero com menos de tres componentes nao conta.
                "<h3 class=\"rfb_subheader\">12.2</h3>"
        })
        @DisplayName("Sem o bloco ou sem versao valida retorna null")
        void semVersaoRetornaNull(String html) {
            assertThat(parser.extractVersion(html, CLASSE)).isNull();
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("HTML nulo ou vazio retorna null")
        void htmlInvalidoRetornaNull(String html) {
            assertThat(parser.extractVersion(html, CLASSE)).isNull();
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("Classe nula ou vazia retorna null")
        void classeInvalidaRetornaNull(String classe) {
            assertThat(parser.extractVersion("<h3 class=\"rfb_subheader\">12.2.6</h3>", classe))
                    .isNull();
        }

        @Test
        @DisplayName("A classe e um seletor literal, nao expressao regular")
        void classeNaoEhRegex() {
            // Um ponto na classe nao pode virar "qualquer caractere".
            assertThat(parser.extractVersion("<h3 class=\"rfbXsubheader\">12.2.6</h3>",
                    "rfb.subheader")).isNull();
            assertThat(parser.extractVersion("<h3 class=\"rfb.subheader\">12.2.6</h3>",
                    "rfb.subheader")).isEqualTo("12.2.6");
        }

        @Test
        @DisplayName("HTML malformado e tolerado, sem lancar excecao")
        void htmlMalformadoEhTolerado() {
            String html = "<h3 class=\"rfb_subheader\">12.2.6<h3><div><span>";

            assertThat(parser.extractVersion(html, CLASSE)).isEqualTo("12.2.6");
        }

        @Test
        @DisplayName("Conteudo hostil no bloco nao vira versao")
        void conteudoHostilNaoViraVersao() {
            String html = """
                    <h3 class="rfb_subheader"><script>alert('12.2.6')</script></h3>
                    """;

            // O texto do script e executavel no navegador, nao na JVM: aqui o
            // que importa e nao propagar o conteudo bruto para o popup.
            assertThat(parser.extractVersion(html, CLASSE)).isNull();
        }

        @Test
        @DisplayName("Entidades HTML sao decodificadas antes da extracao")
        void entidadesHtmlSaoDecodificadas() {
            String html = "<h3 class=\"rfb_subheader\">Vers\u00e3o&#160;12.2.6</h3>";

            assertThat(parser.extractVersion(html, CLASSE)).isEqualTo("12.2.6");
        }
    }

    @Nested
    @DisplayName("Extracao do link de download")
    class ExtracaoDeLink {

        @ParameterizedTest(name = "[{index}]")
        @MethodSource("htmlsComLink")
        @DisplayName("Extrai o link oficial de download")
        void extraiLinkDeDownload(String html, String esperado) {
            assertThat(parser.extractDownloadUrl(html)).isEqualTo(esperado);
        }

        private static Stream<Arguments> htmlsComLink() {
            return Stream.of(
                    Arguments.of("<a href=\"https://gov.br/ecf/download\">Baixar</a>",
                            "https://gov.br/ecf/download"),
                    Arguments.of("<a href=\"https://gov.br/x\">P\u00e1gina de download</a>",
                            "https://gov.br/x"),
                    Arguments.of("<a href=\"https://gov.br/sped/ecf.zip\">Instalador</a>",
                            "https://gov.br/sped/ecf.zip"),
                    // Link relativo resolvido contra a base do documento.
                    Arguments.of("<html><head><base href=\"https://www.gov.br/\"></head>"
                                    + "<body><a href=\"/sped/download\">download</a></body></html>",
                            "https://www.gov.br/sped/download")
            );
        }

        @ParameterizedTest(name = "[{index}] link inseguro")
        @ValueSource(strings = {
                "<a href=\"file:///C:/Windows/calc.exe\">download</a>",
                "<a href=\"javascript:alert(1)\">download</a>",
                "<a href=\"ftp://gov.br/download\">download</a>",
                "<a href=\"https://gov.br/pagina\">Outro assunto</a>",
                "<p>Sem links nesta p\u00e1gina</p>"
        })
        @DisplayName("Links inseguros ou nao relacionados sao ignorados")
        void linksInsegurosIgnorados(String html) {
            assertThat(parser.extractDownloadUrl(html)).isNull();
        }

        @Test
        @DisplayName("Pula o link inseguro e usa o proximo valido")
        void pulaLinkInseguro() {
            String html = """
                    <html><body>
                      <a href="javascript:alert(1)">download</a>
                      <a href="https://gov.br/ecf/download">download oficial</a>
                    </body></html>
                    """;

            assertThat(parser.extractDownloadUrl(html)).isEqualTo("https://gov.br/ecf/download");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("HTML nulo ou vazio retorna null")
        void htmlInvalidoRetornaNull(String html) {
            assertThat(parser.extractDownloadUrl(html)).isNull();
        }
    }

    /** Monta uma pagina completa parecida com a do portal. */
    private static String paginaCompleta(String versao) {
        return """
                <!DOCTYPE html>
                <html lang="pt-br">
                  <head><title>SPED - ECF</title></head>
                  <body>
                    <div class="banner">Programas SPED</div>
                    <div class="conteudo">
                      <h3 class="rfb_subheader">\u00daltima vers\u00e3o: %s</h3>
                      <a href="https://www.gov.br/ecf/download">Baixar o instalador</a>
                    </div>
                    <footer>Nota fiscal 3.0</footer>
                  </body>
                </html>
                """.formatted(versao);
    }
}

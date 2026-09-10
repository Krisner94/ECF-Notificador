package app.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Modulo 5 - Seguranca, sanitizacao e prevencao de injection.
 *
 * <p>O texto exibido no popup nativo vem do site da Receita Federal. Estes
 * testes garantem que nada perigoso ou malformado chega a API Win32 e que o
 * programa so abre URLs HTTP/HTTPS.</p>
 */
class TextSanitizerTest {

    @Nested
    @DisplayName("Sanitizacao de texto de exibicao")
    class TextoDeExibicao {

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {
                "Nova versao 12.2.6 disponivel",
                "Texto com acentua\u00e7\u00e3o: vers\u00e3o v\u00e1lida",
                "Numeros 123 e simbolos -_.:/",
        })
        @DisplayName("Texto legitimo passa sem alteracao")
        void textoLegitimoPassaIntacto(String entrada) {
            assertThat(TextSanitizer.sanitizeDisplayText(entrada)).isEqualTo(entrada);
        }

        @ParameterizedTest(name = "[{index}] entrada com caractere de controle")
        @MethodSource("textosComCaracteresDeControle")
        @DisplayName("Caracteres de controle viram espaco e nao quebram o popup")
        void caracteresDeControleSaoNeutralizados(String entrada, String esperado) {
            assertThat(TextSanitizer.sanitizeDisplayText(entrada)).isEqualTo(esperado);
        }

        private static Stream<Arguments> textosComCaracteresDeControle() {
            return Stream.of(
                    // Quebra de linha e tab nao podem reestruturar a janela nativa.
                    Arguments.of("linha1\nlinha2", "linha1 linha2"),
                    Arguments.of("linha1\r\nlinha2", "linha1 linha2"),
                    Arguments.of("coluna1\tcoluna2", "coluna1 coluna2"),
                    // Caractere de controle C0 e C1 desaparece.
                    Arguments.of("antes\u0007depois", "antesdepois"),
                    Arguments.of("antes\u001bdepois", "antesdepois"),
                    Arguments.of("antes\u009fdepois", "antesdepois")
            );
        }

        @ParameterizedTest(name = "[{index}] espacos em excesso")
        @CsvSource(delimiter = '|', value = {
                "com espacos nas pontas|com espacos nas pontas",
                "espacos    internos|espacos internos",
                "muitos          espacos|muitos espacos",
        })
        @DisplayName("Espacos em excesso sao normalizados")
        void espacosNormalizados(String entrada, String esperado) {
            assertThat(TextSanitizer.sanitizeDisplayText(entrada)).isEqualTo(esperado);
        }

        @Test
        @DisplayName("Texto acima do limite e truncado, sem estourar memoria")
        void textoLongoEhTruncado() {
            String enorme = "a".repeat(10_000);

            String resultado = TextSanitizer.sanitizeDisplayText(enorme);

            assertThat(resultado).hasSize(TextSanitizer.MAX_TEXT_LENGTH);
            assertThat(resultado).endsWith("...");
        }

        @Test
        @DisplayName("Texto exatamente no limite nao e truncado")
        void textoNoLimiteNaoEhTruncado() {
            String limite = "b".repeat(TextSanitizer.MAX_TEXT_LENGTH);

            assertThat(TextSanitizer.sanitizeDisplayText(limite)).isEqualTo(limite);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   ", "\n\n", "\u0000\u0001"})
        @DisplayName("Entrada vazia ou so com controle devolve string vazia, nunca nulo")
        void entradaVaziaDevolveStringVazia(String entrada) {
            assertThat(TextSanitizer.sanitizeDisplayText(entrada)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Validacao de versao extraida da pagina")
    class ValidacaoDeVersao {

        @ParameterizedTest(name = "aceita {0}")
        @ValueSource(strings = {
                "12.2.6",
                "12.2",
                "1.0.0",
                "10.0.1",
                "12.2.6-beta",
                "12.2.6-rc1",
                " 12.2.6 ",
        })
        @DisplayName("Versoes legitimas sao aceitas e apara das")
        void versoesLegitimasAceitas(String entrada) {
            assertThat(TextSanitizer.sanitizeVersion(entrada))
                    .isEqualTo(entrada.trim());
        }

        @ParameterizedTest(name = "rejeita ''{0}''")
        @NullSource
        @ValueSource(strings = {
                "",
                "   ",
                "sem versao aqui",
                "v12.2.6",
                "12..6",
                "abc.def.ghi",
                "12.2.6<script>alert(1)</script>",
                "<h3>12.2.6</h3>",
                "12.2.6' OR 1=1 --",
        })
        @DisplayName("Valores que nao sao versao sao rejeitados")
        void valoresInvalidosRejeitados(String entrada) {
            assertThat(TextSanitizer.sanitizeVersion(entrada)).isNull();
        }

        @Test
        @DisplayName("Versao absurdamente longa e rejeitada em vez de exibida")
        void versaoLongaEhRejeitada() {
            String enorme = "1." + "9".repeat(500);

            assertThat(TextSanitizer.sanitizeVersion(enorme)).isNull();
        }

        @Test
        @DisplayName("Versao no limite exato de tamanho e aceita")
        void versaoNoLimiteEhAceita() {
            String versao = "1" + "0".repeat(TextSanitizer.MAX_VERSION_LENGTH - 3) + ".0";

            assertThat(TextSanitizer.sanitizeVersion(versao)).isNotNull();
        }

        @ParameterizedTest(name = "[{index}] sufixo preservado")
        @CsvSource(delimiter = '|', value = {
                "12.2.6-beta | 12.2.6-beta",
                "12.2.6      | 12.2.6",
        })
        @DisplayName("Sufixo de release e preservado na exibicao")
        void sufixoPreservado(String entrada, String esperado) {
            assertThat(TextSanitizer.sanitizeVersion(entrada)).isEqualTo(esperado);
        }
    }

    @Nested
    @DisplayName("Validacao de URL do instalador")
    class ValidacaoDeUrl {

        @ParameterizedTest(name = "aceita {0}")
        @ValueSource(strings = {
                "https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/download/sped/ecf",
                "http://www.gov.br/ecf",
                "https://gov.br",
                "https://gov.br/pagina?param=1#ancora",
        })
        @DisplayName("URLs HTTP e HTTPS validas sao aceitas")
        void urlsValidasAceitas(String url) {
            assertThat(TextSanitizer.isSafeHttpUrl(url)).isTrue();
        }

        @ParameterizedTest(name = "rejeita ''{0}''")
        @NullSource
        @ValueSource(strings = {
                "",
                "   ",
                "nao-e-url",
                "www.gov.br/ecf",
                "//gov.br/ecf",
                // Esquemas perigosos: abririam programas locais ou executariam codigo.
                "file:///C:/Windows/System32/calc.exe",
                "javascript:alert(1)",
                "jar:file:///tmp/x.jar!/",
                "ftp://gov.br/ecf",
                "data:text/html,<script>alert(1)</script>",
                "vbscript:msgbox(1)",
                // Esquema sem host.
                "https://",
                "http://",
                "https:///caminho",
        })
        @DisplayName("Esquemas perigosos e URLs malformadas sao recusados")
        void urlsPerigosasRecusadas(String url) {
            assertThat(TextSanitizer.isSafeHttpUrl(url)).isFalse();
            assertThat(TextSanitizer.sanitizeUrlOrNull(url)).isNull();
        }

        @Test
        @DisplayName("Caractere de controle embutido na URL e removido antes da checagem")
        void caractereDeControleNaUrlEhRemovido() {
            // Um \n no meio nao pode servir para "escapar" do filtro de esquema.
            String url = "https://www.gov.br/\necf";

            assertThat(TextSanitizer.isSafeHttpUrl(url)).isTrue();
            assertThat(TextSanitizer.sanitizeUrlOrNull(url))
                    .isEqualTo("https://www.gov.br/ecf");
        }

        @Test
        @DisplayName("Esquema malicioso disfarcado com controle continua recusado")
        void esquemaPerigosoComControleRecusado() {
            assertThat(TextSanitizer.isSafeHttpUrl("java\nscript:alert(1)")).isFalse();
            assertThat(TextSanitizer.isSafeHttpUrl("file\n:///c:/x")).isFalse();
        }
    }
}

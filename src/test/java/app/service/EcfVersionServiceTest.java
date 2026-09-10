package app.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Testes da leitura da versao instalada da ECF.
 *
 * <p>O parsing em si e testado sobre o metodo puro {@code extractVersion}, sem
 * tocar em disco; o acesso a arquivo e coberto separadamente com um diretorio
 * temporario.</p>
 */
class EcfVersionServiceTest {

    private final EcfVersionService service = new EcfVersionService();

    // ===================== Parsing (sem disco) =====================

    @ParameterizedTest(name = "[{index}] esperado \"{1}\"")
    @MethodSource("conteudosComVersao")
    @DisplayName("Extrai o primeiro numero de versao encontrado no texto")
    void extraiPrimeiraVersao(String conteudo, String esperado) {
        assertEquals(esperado, service.extractVersion(conteudo));
    }

    private static Stream<Arguments> conteudosComVersao() {
        return Stream.of(
                // Formato real do response.varfile (install4j).
                Arguments.of("# install4j response file for Escrituracao Digital ECF 12.2.5", "12.2.5"),
                Arguments.of("ECF 10.1.3 instalada", "10.1.3"),
                // Versao com dois componentes e preservada como veio.
                Arguments.of("ECF 12.2 instalada", "12.2"),
                Arguments.of("versao=3.0", "3.0"),
                // O primeiro numero encontrado ganha, mesmo que haja outro depois.
                Arguments.of("Nota fiscal 12.2.6 (schema 1.0)", "12.2.6"),
                // Numero no meio de um caminho de diretorio continua valendo.
                Arguments.of("sys.installationDir=C:\\SpedECF 12.2.1", "12.2.1"),
                // Versao com quatro componentes: os tres primeiros sao lidos.
                Arguments.of("ECF 12.2.5.1", "12.2.5")
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "nenhum numero aqui", "apenas texto"})
    @DisplayName("Texto nulo, vazio ou sem numero retorna null")
    void textoSemVersaoRetornaNull(String conteudo) {
        assertNull(service.extractVersion(conteudo));
    }

    // ===================== Leitura de arquivo =====================

    @ParameterizedTest(name = "conteudo: {0}")
    @ValueSource(strings = {
            "ECF 12.2.5",
            "ECF 12.2",
            "ECF 1.0.0"
    })
    @DisplayName("Le a versao de um arquivo real")
    void leVersaoDoArquivo(String conteudo, @TempDir Path tempDir) throws IOException {
        Path arquivo = tempDir.resolve("response.varfile");
        Files.writeString(arquivo, conteudo, StandardCharsets.UTF_8);

        String esperado = conteudo.substring("ECF ".length());
        assertEquals(esperado, service.getInstalledVersion(arquivo.toString()));
    }

    @Test
    @DisplayName("Le a versao no formato do arquivo response.varfile")
    void leVersaoDoArquivoDeInstalacao(@TempDir Path tempDir) throws IOException {
        Path arquivo = tempDir.resolve("response.varfile");
        Files.writeString(arquivo,
                "# install4j response file for Escrituracao Digital ECF 12.2.5"
                        + System.lineSeparator()
                        + "sys.installationDir=C\\:\\\\SpedECF"
                        + System.lineSeparator(),
                StandardCharsets.UTF_8);

        assertEquals("12.2.5", service.getInstalledVersion(arquivo.toString()));
    }

    @Test
    @DisplayName("Arquivo inexistente retorna null")
    void arquivoInexistenteRetornaNull(@TempDir Path tempDir) {
        Path arquivo = tempDir.resolve("nao-existe.txt");
        assertNull(service.getInstalledVersion(arquivo.toString()));
    }

    @Test
    @DisplayName("Arquivo sem numero de versao retorna null")
    void arquivoSemVersaoRetornaNull(@TempDir Path tempDir) throws IOException {
        Path arquivo = tempDir.resolve("vazio.txt");
        Files.writeString(arquivo, "nenhum numero aqui", StandardCharsets.UTF_8);

        assertNull(service.getInstalledVersion(arquivo.toString()));
    }

    @Test
    @DisplayName("Texto acentuado nao interfere na leitura da versao")
    void acentosNaoInterferem(@TempDir Path tempDir) throws IOException {
        Path arquivo = tempDir.resolve("acentuado.txt");
        Files.writeString(arquivo, "Escritura\u00e7\u00e3o Digital ECF 12.2.5 instalada",
                StandardCharsets.UTF_8);

        assertEquals("12.2.5", service.getInstalledVersion(arquivo.toString()));
    }

    @Test
    @DisplayName("Diretorio no lugar de arquivo retorna null")
    void diretorioRetornaNull(@TempDir Path tempDir) {
        // Ler um diretorio como arquivo lanca IOException: deve virar null.
        assertNull(service.getInstalledVersion(tempDir.toString()));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("Caminho nulo ou em branco retorna null sem excecao")
    void caminhoNuloOuEmBrancoRetornaNull(String caminho) {
        assertNull(service.getInstalledVersion(caminho));
    }

    @ParameterizedTest(name = "caminho invalido (indice {index})")
    @ValueSource(strings = {"C:\\caminho\\invalido\\<>\u0000", "\u0000"})
    @DisplayName("Caminho sintaticamente invalido retorna null em vez de propagar excecao")
    void caminhoInvalidoRetornaNull(String caminho) {
        // O contrato do servico e nunca derrubar a verificacao por causa do
        // caminho configurado pelo usuario.
        assertNull(service.getInstalledVersion(caminho));
    }
}

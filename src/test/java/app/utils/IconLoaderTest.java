package app.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Testes do carregador de icones.
 *
 * <p>Cobrem a deteccao de formato, o parsing do container ICO (que o ImageIO
 * nao entende) e o tratamento de entradas invalidas - a parte critica para o
 * icone da bandeja e do popup de atualizacao.</p>
 */
class IconLoaderTest {

    // ===================== Entradas invalidas =====================

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("Caminho nulo ou em branco retorna null sem lancar excecao")
    void caminhoNuloOuEmBrancoRetornaNull(String caminho) {
        assertNull(IconLoader.load(caminho));
    }

    @ParameterizedTest(name = "arquivo inexistente: {0}")
    @ValueSource(strings = {
            "nao-existe-este-icone.ico",
            "nao-existe.png",
            "pasta/inexistente/icone.ico"
    })
    @DisplayName("Arquivo inexistente retorna null")
    void arquivoInexistenteRetornaNull(String caminho) {
        assertNull(IconLoader.load(caminho));
    }

    @ParameterizedTest(name = "extensao {0}")
    @ValueSource(strings = {"foo.png", "bar.ico", "baz.gif", "qux.jpg", "quux.bmp"})
    @DisplayName("Extensoes suportadas nao quebram o carregamento")
    void extensoesSuportadasNaoQuebram(String caminho) {
        // Nao deve lancar excecao; retorna null porque o arquivo nao existe.
        assertNull(IconLoader.load(caminho));
    }

    @ParameterizedTest(name = "tamanho preferido {0}")
    @ValueSource(ints = {-1, 0, 1, 4096})
    @DisplayName("Tamanho preferido invalido nao causa erro fatal")
    void tamanhoPreferidoInvalido(int tamanho) {
        assertNull(IconLoader.load("inexistente.ico", tamanho));
    }

    @ParameterizedTest(name = "conteudo invalido ({0})")
    @ValueSource(strings = {"arquivo-vazio.bin", "texto.txt", "corrompido.ico"})
    @DisplayName("Arquivo com conteudo que nao e imagem retorna null")
    void conteudoInvalidoRetornaNull(String nome, @TempDir Path tempDir) throws IOException {
        Path arquivo = tempDir.resolve(nome);
        Files.write(arquivo, new byte[] {0x01, 0x02, 0x03});

        assertNull(IconLoader.load(arquivo.toString()));
    }

    // ===================== Deteccao de formato real =====================

    @ParameterizedTest(name = "PNG {0}x{1}")
    @CsvSource(delimiter = '|', value = {"16|16", "32|32", "48|48", "256|256"})
    @DisplayName("Carrega PNG real com as dimensoes corretas")
    void carregaPngReal(int largura, int altura, @TempDir Path tempDir) throws IOException {
        Path arquivo = tempDir.resolve("icone.png");
        ImageIO.write(criarImagem(largura, altura), "png", arquivo.toFile());

        Image imagem = IconLoader.load(arquivo.toString());

        assertNotNull(imagem, "Um PNG valido deveria ser carregado.");
        assertEquals(largura, imagem.getWidth(null));
        assertEquals(altura, imagem.getHeight(null));
    }

    @ParameterizedTest(name = "BMP {0}x{1}")
    @CsvSource(delimiter = '|', value = {"16|16", "32|32"})
    @DisplayName("Carrega BMP real com as dimensoes corretas")
    void carregaBmpReal(int largura, int altura, @TempDir Path tempDir) throws IOException {
        Path arquivo = tempDir.resolve("icone.bmp");
        ImageIO.write(criarImagemRgb(largura, altura), "bmp", arquivo.toFile());

        Image imagem = IconLoader.load(arquivo.toString());

        assertNotNull(imagem, "Um BMP valido deveria ser carregado.");
        assertEquals(largura, imagem.getWidth(null));
        assertEquals(altura, imagem.getHeight(null));
    }

    @Test
    @DisplayName("Caminho com espacos no meio e carregado")
    void caminhoComEspacosNoMeio(@TempDir Path tempDir) throws IOException {
        Path pasta = tempDir.resolve("pasta com espacos");
        Files.createDirectories(pasta);
        Path arquivo = pasta.resolve("meu icone.png");
        ImageIO.write(criarImagem(16, 16), "png", arquivo.toFile());

        assertNotNull(IconLoader.load(arquivo.toString()));
    }

    @Test
    @DisplayName("Caminho com espacos nas pontas e normalizado")
    void caminhoComEspacosNasPontas(@TempDir Path tempDir) throws IOException {
        Path arquivo = tempDir.resolve("icone.png");
        ImageIO.write(criarImagem(16, 16), "png", arquivo.toFile());

        assertNotNull(IconLoader.load("  " + arquivo + "  "));
    }

    @Test
    @DisplayName("Recurso nao-imagem do classpath nao gera excecao")
    void recursoNaoImagemNaoGeraExcecao() {
        // appSettings.json existe no classpath: deve chegar ao decode e
        // devolver null por nao ser imagem, sem lancar excecao.
        assertNull(IconLoader.load("appSettings.json"));
    }

    @Test
    @DisplayName("Carregar o mesmo arquivo duas vezes devolve imagens independentes")
    void carregamentosSaoIndependentes(@TempDir Path tempDir) throws IOException {
        Path arquivo = tempDir.resolve("icone.png");
        ImageIO.write(criarImagem(16, 16), "png", arquivo.toFile());

        Image primeira = IconLoader.load(arquivo.toString());
        Image segunda = IconLoader.load(arquivo.toString());

        assertNotNull(primeira);
        assertNotNull(segunda);
        assertNotSame(primeira, segunda, "Cada chamada deveria produzir uma nova imagem.");
        assertEquals(primeira.getWidth(null), segunda.getWidth(null));
    }

    // ===================== Icone gerado (fallback) =====================

    @ParameterizedTest(name = "tamanho {0}")
    @ValueSource(ints = {16, 32, 48})
    @DisplayName("Icone solido tem o tamanho pedido")
    void iconeSolidoTemTamanhoPedido(int tamanho) {
        Image imagem = IconLoader.createSolidIcon(Color.BLUE, tamanho);

        assertNotNull(imagem);
        assertEquals(tamanho, imagem.getWidth(null));
        assertEquals(tamanho, imagem.getHeight(null));
    }

    @Test
    @DisplayName("Icone solido usa a cor informada")
    void iconeSolidoUsaCorInformada() {
        BufferedImage imagem = (BufferedImage) IconLoader.createSolidIcon(new Color(10, 20, 30), 8);

        assertEquals(new Color(10, 20, 30).getRGB(), imagem.getRGB(0, 0));
        assertEquals(new Color(10, 20, 30).getRGB(), imagem.getRGB(7, 7));
    }

    @ParameterizedTest(name = "tamanho invalido {0}")
    @ValueSource(ints = {0, -5})
    @DisplayName("Tamanho invalido do icone solido cai no padrao")
    void iconeSolidoTamanhoInvalido(int tamanho) {
        assertEquals(32, IconLoader.createSolidIcon(Color.RED, tamanho).getWidth(null));
    }

    @Test
    @DisplayName("Icone de fallback e estavel para a mesma semente")
    void iconeDeFallbackEhEstavel() {
        BufferedImage primeira = (BufferedImage) IconLoader.createFallbackIcon("ECF Notificador", 16);
        BufferedImage segunda = (BufferedImage) IconLoader.createFallbackIcon("ECF Notificador", 16);

        // A cor deriva do texto, entao reiniciar o programa nao muda o icone.
        assertEquals(primeira.getRGB(0, 0), segunda.getRGB(0, 0));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "ECF", "aplica\u00e7\u00e3o"})
    @DisplayName("Icone de fallback funciona com qualquer semente")
    void iconeDeFallbackAceitaQualquerSemente(String semente) {
        Image imagem = IconLoader.createFallbackIcon(semente, 16);

        assertNotNull(imagem);
        assertEquals(16, imagem.getWidth(null));
    }

    // ===================== Container ICO =====================

    @Nested
    @DisplayName("Container ICO")
    class ContainerIco {

        @Test
        @DisplayName("ICO com imagem PNG interna e carregado")
        void icoComPngInterno(@TempDir Path tempDir) throws IOException {
            Path arquivo = tempDir.resolve("icone.ico");
            Files.write(arquivo, construirIco(criarImagem(32, 32)));

            Image imagem = IconLoader.load(arquivo.toString());

            assertNotNull(imagem, "Um ICO valido deveria ser carregado.");
            assertEquals(32, imagem.getWidth(null));
            assertEquals(32, imagem.getHeight(null));
        }

        @ParameterizedTest(name = "imagem interna {0}x{1}")
        @CsvSource(delimiter = '|', value = {"16|16", "32|32", "48|48"})
        @DisplayName("Carrega ICO preservando as dimensoes da imagem interna")
        void icoPreservaDimensoes(int largura, int altura, @TempDir Path tempDir) throws IOException {
            Path arquivo = tempDir.resolve("icone-" + largura + ".ico");
            Files.write(arquivo, construirIco(criarImagem(largura, altura)));

            Image imagem = IconLoader.load(arquivo.toString());

            assertNotNull(imagem);
            assertEquals(largura, imagem.getWidth(null));
            assertEquals(altura, imagem.getHeight(null));
        }

        @ParameterizedTest(name = "preferido {0} escolhe a imagem de {1}")
        @CsvSource(delimiter = '|', value = {"16|16", "32|32", "48|48", "64|64"})
        @DisplayName("Escolhe a menor imagem que atende o tamanho preferido")
        void escolheTamanhoMaisProximo(int preferido, int esperado, @TempDir Path tempDir)
                throws IOException {
            Path arquivo = tempDir.resolve("multi.ico");
            Files.write(arquivo, construirIco(
                    criarImagem(16, 16), criarImagem(32, 32),
                    criarImagem(48, 48), criarImagem(64, 64)));

            Image imagem = IconLoader.load(arquivo.toString(), preferido);

            assertNotNull(imagem);
            assertEquals(esperado, imagem.getWidth(null),
                    "Para o tamanho preferido " + preferido + " a imagem escolhida esta errada.");
        }

        @Test
        @DisplayName("Tamanho preferido maior que todas as imagens usa a maior disponivel")
        void preferidoMaiorQueTodasUsaAMaior(@TempDir Path tempDir) throws IOException {
            Path arquivo = tempDir.resolve("pequeno.ico");
            Files.write(arquivo, construirIco(criarImagem(16, 16), criarImagem(32, 32)));

            Image imagem = IconLoader.load(arquivo.toString(), 256);

            assertNotNull(imagem);
            assertEquals(32, imagem.getWidth(null));
        }

        @Test
        @DisplayName("ICO truncado retorna null em vez de lancar excecao")
        void icoTruncadoRetornaNull(@TempDir Path tempDir) throws IOException {
            byte[] completo = construirIco(criarImagem(32, 32));
            byte[] truncado = new byte[10];
            System.arraycopy(completo, 0, truncado, 0, truncado.length);

            Path arquivo = tempDir.resolve("truncado.ico");
            Files.write(arquivo, truncado);

            assertNull(IconLoader.load(arquivo.toString()));
        }

        @ParameterizedTest(name = "cabecalho invalido (indice {index})")
        @MethodSource("cabecalhosInvalidos")
        @DisplayName("Cabecalho ICO invalido retorna null")
        void cabecalhoInvalidoRetornaNull(byte[] conteudo, @TempDir Path tempDir) throws IOException {
            Path arquivo = tempDir.resolve("invalido.ico");
            Files.write(arquivo, conteudo);

            assertNull(IconLoader.load(arquivo.toString()));
        }

        private static Stream<Arguments> cabecalhosInvalidos() {
            return Stream.of(
                    // Assinatura ICO (0,0,1,0) mas sem entradas no diretorio.
                    Arguments.of(new byte[] {0, 0, 1, 0, 0, 0}),
                    // Assinatura ICO com contagem de imagens zerada.
                    Arguments.of(new byte[] {0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}),
                    // Tipo invalido (2 = cursor, nao icone).
                    Arguments.of(new byte[] {0, 0, 2, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0})
            );
        }
    }

    // ===================== Utilitarios =====================

    /** Cria uma imagem ARGB com padrao xadrez (evita mascara de erro de offset). */
    private static BufferedImage criarImagem(int largura, int altura) {
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < altura; y++) {
            for (int x = 0; x < largura; x++) {
                int cor = ((x + y) % 2 == 0) ? 0xFF1E88E5 : 0xFFFFFFFF;
                imagem.setRGB(x, y, cor);
            }
        }
        return imagem;
    }

    /** Variante RGB, exigida pelo codificador BMP do Java. */
    private static BufferedImage criarImagemRgb(int largura, int altura) {
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < altura; y++) {
            for (int x = 0; x < largura; x++) {
                int cor = ((x + y) % 2 == 0) ? 0x1E88E5 : 0xFFFFFF;
                imagem.setRGB(x, y, cor);
            }
        }
        return imagem;
    }

    /**
     * Monta um arquivo ICO valido, com uma imagem PNG interna por entrada.
     * Layout: ICONDIR (6 bytes) + ICONDIRENTRY (16 bytes cada) + dados.
     */
    private static byte[] construirIco(BufferedImage... imagens) throws IOException {
        byte[][] dados = new byte[imagens.length][];
        for (int i = 0; i < imagens.length; i++) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            ImageIO.write(imagens[i], "png", buffer);
            dados[i] = buffer.toByteArray();
        }

        int cabecalho = 6 + (16 * imagens.length);
        int total = cabecalho;
        for (byte[] dado : dados) {
            total += dado.length;
        }

        byte[] ico = new byte[total];
        ico[2] = 1;                       // tipo 1 = icone
        ico[4] = (byte) imagens.length;   // quantidade de imagens

        int offset = cabecalho;
        for (int i = 0; i < imagens.length; i++) {
            int entrada = 6 + (16 * i);
            int largura = imagens[i].getWidth();
            int altura = imagens[i].getHeight();
            ico[entrada] = (byte) (largura >= 256 ? 0 : largura);
            ico[entrada + 1] = (byte) (altura >= 256 ? 0 : altura);
            escreverInt(ico, entrada + 8, dados[i].length);
            escreverInt(ico, entrada + 12, offset);
            System.arraycopy(dados[i], 0, ico, offset, dados[i].length);
            offset += dados[i].length;
        }
        return ico;
    }

    private static void escreverInt(byte[] destino, int posicao, int valor) {
        destino[posicao] = (byte) (valor & 0xFF);
        destino[posicao + 1] = (byte) ((valor >> 8) & 0xFF);
        destino[posicao + 2] = (byte) ((valor >> 16) & 0xFF);
        destino[posicao + 3] = (byte) ((valor >> 24) & 0xFF);
    }
}

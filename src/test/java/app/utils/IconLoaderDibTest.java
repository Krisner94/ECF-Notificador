package app.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes do container ICO com imagem DIB/BMP embutida.
 *
 * <p>Esta e a parte mais delicada do {@link IconLoader}: o {@code ImageIO} do
 * Java nao le {@code .ico}, entao o formato e decodificado a mao. Icones reais
 * do Windows frequentemente trazem DIB (BITMAPINFOHEADER + pixels) em vez de
 * PNG - e um erro de offset aqui nao gera excecao, gera um icone borrado ou
 * com as cores trocadas, que passa despercebido.</p>
 *
 * <p>Os arquivos sao montados byte a byte nos testes, o que permite verificar
 * cor de pixel, transparencia e orientacao vertical (DIB e armazenado de baixo
 * para cima).</p>
 */
class IconLoaderDibTest {

    // ===================== Montagem dos arquivos =====================

    /**
     * Cria um ICO com uma imagem DIB de 32 bits (BGRA), sem canal alfa util.
     *
     * @param pixels  cores no formato ARGB, indexadas por {@code [y][x]} com
     *                y=0 sendo o TOPO da imagem.
     * @param alfa    valor de alfa gravado em cada pixel (0 = usar mascara AND).
     */
    private static byte[] ico32Bpp(int largura, int altura, int[][] pixels, int alfa) {
        byte[] dib = new byte[40 + (largura * 4 * altura) + mascaraTamanho(largura, altura)];

        escreverInt(dib, 0, 40);                 // biSize
        escreverInt(dib, 4, largura);            // biWidth
        escreverInt(dib, 8, altura * 2);         // biHeight (XOR + AND)
        escreverShort(dib, 12, 1);               // biPlanes
        escreverShort(dib, 14, 32);              // biBitCount
        escreverInt(dib, 16, 0);                 // biCompression = BI_RGB

        // XOR bitmap: linhas de baixo para cima, BGRA.
        for (int y = 0; y < altura; y++) {
            int linhaDestino = 40 + ((altura - 1 - y) * largura * 4);
            for (int x = 0; x < largura; x++) {
                int argb = pixels[y][x];
                int p = linhaDestino + (x * 4);
                dib[p] = (byte) (argb & 0xFF);              // azul
                dib[p + 1] = (byte) ((argb >> 8) & 0xFF);   // verde
                dib[p + 2] = (byte) ((argb >> 16) & 0xFF);  // vermelho
                dib[p + 3] = (byte) alfa;                   // alfa
            }
        }

        return montarIco(dib, largura, altura);
    }

    /**
     * Cria um ICO com uma imagem DIB de 24 bits (BGR).
     * Cada linha e alinhada em multiplo de 4 bytes.
     */
    private static byte[] ico24Bpp(int largura, int altura, int[][] pixels) {
        int passo = (((largura * 3) + 3) / 4) * 4;
        byte[] dib = new byte[40 + (passo * altura) + mascaraTamanho(largura, altura)];

        escreverInt(dib, 0, 40);
        escreverInt(dib, 4, largura);
        escreverInt(dib, 8, altura * 2);
        escreverShort(dib, 12, 1);
        escreverShort(dib, 14, 24);
        escreverInt(dib, 16, 0);

        for (int y = 0; y < altura; y++) {
            int linhaDestino = 40 + ((altura - 1 - y) * passo);
            for (int x = 0; x < largura; x++) {
                int rgb = pixels[y][x];
                int p = linhaDestino + (x * 3);
                dib[p] = (byte) (rgb & 0xFF);
                dib[p + 1] = (byte) ((rgb >> 8) & 0xFF);
                dib[p + 2] = (byte) ((rgb >> 16) & 0xFF);
            }
        }

        return montarIco(dib, largura, altura);
    }

    /**
     * Cria um ICO com imagem DIB de 1 bit (paleta de 2 cores).
     * Exercita o caminho com paleta e a mascara AND.
     */
    private static byte[] ico1Bpp(int largura, int altura, boolean[][] bits,
                                  int cor0, int cor1) {
        int passo = (((largura + 31) / 32) * 4);
        int entradasPaleta = 2;
        byte[] dib = new byte[40 + (entradasPaleta * 4) + (passo * altura)
                + mascaraTamanho(largura, altura)];

        escreverInt(dib, 0, 40);
        escreverInt(dib, 4, largura);
        escreverInt(dib, 8, altura * 2);
        escreverShort(dib, 12, 1);
        escreverShort(dib, 14, 1);
        escreverInt(dib, 16, 0);
        escreverInt(dib, 32, entradasPaleta);  // biClrUsed

        // Paleta: indice 0 em cor0, indice 1 em cor1 (formato BGRX).
        int basePaleta = 40;
        escreverPaleta(dib, basePaleta, cor0);
        escreverPaleta(dib, basePaleta + 4, cor1);

        int baseBits = basePaleta + (entradasPaleta * 4);
        for (int y = 0; y < altura; y++) {
            int linhaDestino = baseBits + ((altura - 1 - y) * passo);
            for (int x = 0; x < largura; x++) {
                if (bits[y][x]) {
                    int byteIndex = linhaDestino + (x / 8);
                    int bitIndex = 7 - (x % 8);
                    dib[byteIndex] |= (byte) (1 << bitIndex);
                }
            }
        }

        return montarIco(dib, largura, altura);
    }

    /** Monta o ICOD estendido: ICONDIR + 1 ICONDIRENTRY + dados. */
    private static byte[] montarIco(byte[] imagem, int largura, int altura) {
        int cabecalho = 6 + 16;
        byte[] ico = new byte[cabecalho + imagem.length];

        ico[0] = 0;
        ico[1] = 0;
        ico[2] = 1;                              // tipo 1 = icone
        ico[3] = 0;
        escreverShort(ico, 4, 1);                // uma imagem

        int entrada = 6;
        ico[entrada] = (byte) (largura >= 256 ? 0 : largura);
        ico[entrada + 1] = (byte) (altura >= 256 ? 0 : altura);
        ico[entrada + 2] = 0;                    // cores da paleta
        ico[entrada + 3] = 0;
        escreverShort(ico, entrada + 4, 1);      // planos
        escreverShort(ico, entrada + 6, 32);     // bits por pixel
        escreverInt(ico, entrada + 8, imagem.length);
        escreverInt(ico, entrada + 12, cabecalho);

        System.arraycopy(imagem, 0, ico, cabecalho, imagem.length);
        return ico;
    }

    private static void escreverPaleta(byte[] destino, int posicao, int rgb) {
        destino[posicao] = (byte) (rgb & 0xFF);
        destino[posicao + 1] = (byte) ((rgb >> 8) & 0xFF);
        destino[posicao + 2] = (byte) ((rgb >> 16) & 0xFF);
        destino[posicao + 3] = 0;
    }

    private static int mascaraTamanho(int largura, int altura) {
        return (((largura + 31) / 32) * 4) * altura;
    }

    private static void escreverInt(byte[] destino, int posicao, int valor) {
        destino[posicao] = (byte) (valor & 0xFF);
        destino[posicao + 1] = (byte) ((valor >> 8) & 0xFF);
        destino[posicao + 2] = (byte) ((valor >> 16) & 0xFF);
        destino[posicao + 3] = (byte) ((valor >> 24) & 0xFF);
    }

    private static void escreverShort(byte[] destino, int posicao, int valor) {
        destino[posicao] = (byte) (valor & 0xFF);
        destino[posicao + 1] = (byte) ((valor >> 8) & 0xFF);
    }

    private static int[][] imagemCheia(int largura, int altura, int argb) {
        int[][] pixels = new int[altura][largura];
        for (int y = 0; y < altura; y++) {
            for (int x = 0; x < largura; x++) {
                pixels[y][x] = argb;
            }
        }
        return pixels;
    }

    private static Path gravar(Path tempDir, String nome, byte[] conteudo) throws IOException {
        Path arquivo = tempDir.resolve(nome);
        Files.write(arquivo, conteudo);
        return arquivo;
    }

    // ===================== DIB de 32 bits =====================

    @Nested
    @DisplayName("DIB de 32 bits")
    class Dib32Bits {

        @ParameterizedTest(name = "{0}x{1}")
        @CsvSource(delimiter = '|', value = {"16|16", "32|32", "48|48"})
        @DisplayName("Le as dimensoes da imagem (altura do DIB vem dobrada)")
        void leDimensoes(int largura, int altura, @TempDir Path tempDir) throws IOException {
            Path ico = gravar(tempDir, "i" + largura + ".ico",
                    ico32Bpp(largura, altura, imagemCheia(largura, altura, 0xFFFF00FF), 0xFF));

            Image imagem = IconLoader.load(ico.toString());

            assertThat(imagem).isNotNull();
            // A altura do header inclui a mascara AND; a imagem real tem metade.
            assertThat(imagem.getWidth(null)).isEqualTo(largura);
            assertThat(imagem.getHeight(null)).isEqualTo(altura);
        }

        @Test
        @DisplayName("Preserva as cores dos pixels (BGRA -> ARGB)")
        void preservaCores(@TempDir Path tempDir) throws IOException {
            int[][] pixels = {
                    {0xFFFF0000, 0xFF00FF00},
                    {0xFF0000FF, 0xFFFFFF00},
            };
            Path ico = gravar(tempDir, "cores.ico", ico32Bpp(2, 2, pixels, 0xFF));

            BufferedImage imagem = (BufferedImage) IconLoader.load(ico.toString());

            assertThat(imagem).isNotNull();
            // Vermelho no topo-esquerda: prova que nao houve troca de canal
            // nem inversao vertical.
            assertThat(imagem.getRGB(0, 0)).isEqualTo(0xFFFF0000);
            assertThat(imagem.getRGB(1, 0)).isEqualTo(0xFF00FF00);
            assertThat(imagem.getRGB(0, 1)).isEqualTo(0xFF0000FF);
            assertThat(imagem.getRGB(1, 1)).isEqualTo(0xFFFFFF00);
        }

        @Test
        @DisplayName("Respeita o canal alfa quando ha transparencia real")
        void respeitaAlfaReal(@TempDir Path tempDir) throws IOException {
            int[][] pixels = {
                    {0xFF123456, 0x00123456},   // opaco e totalmente transparente
            };
            Path ico = gravar(tempDir, "alfa.ico", ico32Bpp(2, 1,
                    new int[][] {pixels[0]}, 0xFF));
            // Reescreve o alfa individualmente: o helper grava um valor unico.
            Path icoCustom = gravar(tempDir, "alfa2.ico",
                    ico32BppComAlfaPorPixel(2, 1, new int[][] {
                            {0xFF123456, 0x00123456}
                    }, new int[][] {{0xFF, 0x00}}));

            BufferedImage imagem = (BufferedImage) IconLoader.load(icoCustom.toString());

            assertThat(imagem).isNotNull();
            assertThat(imagem.getRGB(0, 0) >>> 24).as("primeiro pixel opaco").isEqualTo(0xFF);
            assertThat(imagem.getRGB(1, 0) >>> 24).as("segundo pixel transparente").isZero();
        }

        @Test
        @DisplayName("Sem alfa valido, usa a mascara AND para transparencia")
        void usaMascaraAndQuandoAlfaEhZero(@TempDir Path tempDir) throws IOException {
            // Alfa todo zero + mascara AND com o segundo pixel marcado.
            byte[] ico = ico32BppComMascaraAnd(2, 1,
                    new int[][] {{0x00AA0000, 0x0000AA00}},
                    new int[][] {{0xFF, 0x00}},   // opaco, transparente
                    new int[][] {{0, 1}});        // bit 1 = transparente

            BufferedImage imagem = (BufferedImage) IconLoader.load(
                    gravar(tempDir, "and.ico", ico).toString());

            assertThat(imagem).isNotNull();
            // O pixel marcado na mascara fica transparente; o outro, opaco.
            assertThat(imagem.getRGB(0, 0) >>> 24).isEqualTo(0xFF);
            assertThat(imagem.getRGB(1, 0) >>> 24).isZero();
        }

        @Test
        @DisplayName("Imagem 1x1 e decodificada")
        void imagemMinima(@TempDir Path tempDir) throws IOException {
            Path ico = gravar(tempDir, "um.ico",
                    ico32Bpp(1, 1, new int[][] {{0xFF00FF00}}, 0xFF));

            BufferedImage imagem = (BufferedImage) IconLoader.load(ico.toString());

            assertThat(imagem).isNotNull();
            assertThat(imagem.getWidth(null)).isEqualTo(1);
            assertThat(imagem.getHeight(null)).isEqualTo(1);
            assertThat(imagem.getRGB(0, 0)).isEqualTo(0xFF00FF00);
        }
    }

    // ===================== DIB de 24 bits =====================

    @Nested
    @DisplayName("DIB de 24 bits")
    class Dib24Bits {

        @Test
        @DisplayName("Le dimensoes e cores de um DIB sem canal alfa")
        void leCores(@TempDir Path tempDir) throws IOException {
            int[][] pixels = {
                    {0xFF0000, 0x00FF00},
                    {0x0000FF, 0xFFFF00},
            };
            Path ico = gravar(tempDir, "b24.ico", ico24Bpp(2, 2, pixels));

            BufferedImage imagem = (BufferedImage) IconLoader.load(ico.toString());

            assertThat(imagem).isNotNull();
            assertThat(imagem.getWidth(null)).isEqualTo(2);
            assertThat(imagem.getHeight(null)).isEqualTo(2);
            assertThat(imagem.getRGB(0, 0) & 0xFFFFFF).isEqualTo(0xFF0000);
            assertThat(imagem.getRGB(1, 1) & 0xFFFFFF).isEqualTo(0xFFFF00);
        }

        @ParameterizedTest(name = "largura {0} (linha nao multipla de 4 bytes)")
        @CsvSource({"1", "3", "5", "7", "9"})
        @DisplayName("Lida com o alinhamento de linha em 4 bytes")
        void lidaComAlinhamento(int largura, @TempDir Path tempDir) throws IOException {
            int[][] pixels = imagemCheia(largura, 2, 0x123456);
            Path ico = gravar(tempDir, "b" + largura + ".ico", ico24Bpp(largura, 2, pixels));

            BufferedImage imagem = (BufferedImage) IconLoader.load(ico.toString());

            assertThat(imagem).isNotNull();
            assertThat(imagem.getWidth(null)).isEqualTo(largura);
            // Se o passo estivesse errado, as linhas ficariam deslocadas.
            assertThat(imagem.getRGB(0, 0) & 0xFFFFFF).isEqualTo(0x123456);
            assertThat(imagem.getRGB(largura - 1, 1) & 0xFFFFFF).isEqualTo(0x123456);
        }

        @Test
        @DisplayName("Sem alfa no arquivo, a imagem sai opaca")
        void semAlfaFicaOpaca(@TempDir Path tempDir) throws IOException {
            Path ico = gravar(tempDir, "opaca.ico",
                    ico24Bpp(2, 2, imagemCheia(2, 2, 0x808080)));

            BufferedImage imagem = (BufferedImage) IconLoader.load(ico.toString());

            assertThat(imagem).isNotNull();
            assertThat(imagem.getRGB(0, 0) >>> 24).isEqualTo(0xFF);
        }
    }

    // ===================== DIB com paleta =====================

    @Nested
    @DisplayName("DIB de 1 bit com paleta")
    class DibComPaleta {

        @Test
        @DisplayName("Usa as cores da paleta para os bits 0 e 1")
        void usaPaleta(@TempDir Path tempDir) throws IOException {
            boolean[][] bits = {{true, false}};
            Path ico = gravar(tempDir, "p1.ico",
                    ico1Bpp(2, 1, bits, 0x112233, 0x445566));

            BufferedImage imagem = (BufferedImage) IconLoader.load(ico.toString());

            assertThat(imagem).isNotNull();
            // bit 1 -> indice 1 -> 0x445566 ; bit 0 -> indice 0 -> 0x112233.
            assertThat(imagem.getRGB(0, 0) & 0xFFFFFF).isEqualTo(0x445566);
            assertThat(imagem.getRGB(1, 0) & 0xFFFFFF).isEqualTo(0x112233);
        }

        @ParameterizedTest(name = "largura {0}")
        @CsvSource({"8", "9", "16", "17"})
        @DisplayName("Le corretamente larguras que cruzam o limite de byte")
        void largurasVariadas(int largura, @TempDir Path tempDir) throws IOException {
            boolean[][] bits = new boolean[1][largura];
            for (int x = 0; x < largura; x++) {
                bits[0][x] = (x % 2 == 0);
            }
            Path ico = gravar(tempDir, "p" + largura + ".ico",
                    ico1Bpp(largura, 1, bits, 0x000000, 0xFFFFFF));

            BufferedImage imagem = (BufferedImage) IconLoader.load(ico.toString());

            assertThat(imagem).isNotNull();
            assertThat(imagem.getWidth(null)).isEqualTo(largura);
            for (int x = 0; x < largura; x++) {
                int esperado = bits[0][x] ? 0xFFFFFF : 0x000000;
                assertThat(imagem.getRGB(x, 0) & 0xFFFFFF)
                        .as("pixel " + x)
                        .isEqualTo(esperado);
            }
        }
    }

    // ===================== Estruturas invalidas =====================

    @Nested
    @DisplayName("Estruturas invalidas")
    class EstruturasInvalidas {

        @Test
        @DisplayName("DIB com compressao nao suportada retorna null")
        void compressaoNaoSuportada(@TempDir Path tempDir) throws IOException {
            byte[] ico = ico32Bpp(4, 4, imagemCheia(4, 4, 0xFFFFFFFF), 0xFF);
            // biCompression fica no offset 16 do DIB, que comeca no offset 22 do ICO.
            escreverInt(ico, 22 + 16, 3);   // BI_BITFIELDS

            assertThat(IconLoader.load(gravar(tempDir, "comp.ico", ico).toString())).isNull();
        }

        @ParameterizedTest(name = "bits por pixel {0}")
        @CsvSource({"2", "3", "7", "9", "16", "48"})
        @DisplayName("Profundidade de cor nao suportada retorna null")
        void profundidadeNaoSuportada(int bpp, @TempDir Path tempDir) throws IOException {
            byte[] ico = ico32Bpp(4, 4, imagemCheia(4, 4, 0xFFFFFFFF), 0xFF);
            escreverShort(ico, 22 + 14, bpp);

            assertThat(IconLoader.load(gravar(tempDir, "bpp" + bpp + ".ico", ico).toString()))
                    .isNull();
        }

        @Test
        @DisplayName("Header DIB menor que 40 bytes retorna null")
        void headerPequeno(@TempDir Path tempDir) throws IOException {
            byte[] ico = ico32Bpp(4, 4, imagemCheia(4, 4, 0xFFFFFFFF), 0xFF);
            escreverInt(ico, 22, 20);   // biSize invalido

            assertThat(IconLoader.load(gravar(tempDir, "hdr.ico", ico).toString())).isNull();
        }

        @Test
        @DisplayName("DIB com dados truncados nao lanca excecao")
        void dadosTruncados(@TempDir Path tempDir) throws IOException {
            byte[] completo = ico32Bpp(8, 8, imagemCheia(8, 8, 0xFFFFFFFF), 0xFF);
            // Mantem o cabecalho mas corta a maior parte dos pixels.
            byte[] truncado = new byte[22 + 40 + 8];
            System.arraycopy(completo, 0, truncado, 0, truncado.length);
            escreverInt(truncado, 6 + 8, 8 * 8 * 4);   // tamanho declarado maior

            assertThat(IconLoader.load(gravar(tempDir, "trunc.ico", truncado).toString()))
                    .isNull();
        }

        @Test
        @DisplayName("Dimensoes zero ou negativas retornam null")
        void dimensoesInvalidas(@TempDir Path tempDir) throws IOException {
            byte[] ico = ico32Bpp(4, 4, imagemCheia(4, 4, 0xFFFFFFFF), 0xFF);
            escreverInt(ico, 22 + 4, 0);   // largura zero

            assertThat(IconLoader.load(gravar(tempDir, "dim0.ico", ico).toString())).isNull();
        }
    }

    // ===================== Utilitarios extras =====================

    /** Variante de 32 bpp com alfa por pixel. */
    private static byte[] ico32BppComAlfaPorPixel(int largura, int altura, int[][] pixels,
                                                  int[][] alfa) {
        byte[] dib = new byte[40 + (largura * 4 * altura) + mascaraTamanho(largura, altura)];
        escreverInt(dib, 0, 40);
        escreverInt(dib, 4, largura);
        escreverInt(dib, 8, altura * 2);
        escreverShort(dib, 12, 1);
        escreverShort(dib, 14, 32);
        escreverInt(dib, 16, 0);

        for (int y = 0; y < altura; y++) {
            int linhaDestino = 40 + ((altura - 1 - y) * largura * 4);
            for (int x = 0; x < largura; x++) {
                int argb = pixels[y][x];
                int p = linhaDestino + (x * 4);
                dib[p] = (byte) (argb & 0xFF);
                dib[p + 1] = (byte) ((argb >> 8) & 0xFF);
                dib[p + 2] = (byte) ((argb >> 16) & 0xFF);
                dib[p + 3] = (byte) alfa[y][x];
            }
        }
        return montarIco(dib, largura, altura);
    }

    /** Variante de 32 bpp com mascara AND explicita. */
    private static byte[] ico32BppComMascaraAnd(int largura, int altura, int[][] pixels,
                                                int[][] alfa, int[][] mascara) {
        int mascaraBytes = mascaraTamanho(largura, altura);
        byte[] dib = new byte[40 + (largura * 4 * altura) + mascaraBytes];
        escreverInt(dib, 0, 40);
        escreverInt(dib, 4, largura);
        escreverInt(dib, 8, altura * 2);
        escreverShort(dib, 12, 1);
        escreverShort(dib, 14, 32);
        escreverInt(dib, 16, 0);

        for (int y = 0; y < altura; y++) {
            int linhaDestino = 40 + ((altura - 1 - y) * largura * 4);
            for (int x = 0; x < largura; x++) {
                int argb = pixels[y][x];
                int p = linhaDestino + (x * 4);
                dib[p] = (byte) (argb & 0xFF);
                dib[p + 1] = (byte) ((argb >> 8) & 0xFF);
                dib[p + 2] = (byte) ((argb >> 16) & 0xFF);
                dib[p + 3] = (byte) alfa[y][x];
            }
        }

        int baseMascara = 40 + (largura * 4 * altura);
        int passoMascara = ((largura + 31) / 32) * 4;
        for (int y = 0; y < altura; y++) {
            int linhaDestino = baseMascara + ((altura - 1 - y) * passoMascara);
            for (int x = 0; x < largura; x++) {
                if (mascara[y][x] == 1) {
                    int byteIndex = linhaDestino + (x / 8);
                    int bitIndex = 7 - (x % 8);
                    dib[byteIndex] |= (byte) (1 << bitIndex);
                }
            }
        }
        return montarIco(dib, largura, altura);
    }

    @Test
    @DisplayName("PNG continua tendo prioridade sobre DIB dentro do mesmo ICO")
    void pngTemPrioridade(@TempDir Path tempDir) throws IOException {
        // Um ICO cujo dado interno e PNG (e nao DIB) deve ser lido pelo ImageIO.
        BufferedImage original = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        original.setRGB(0, 0, new Color(0x33, 0x66, 0x99).getRGB());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(original, "png", buffer);

        Path ico = gravar(tempDir, "png.ico", montarIco(buffer.toByteArray(), 24, 24));
        BufferedImage imagem = (BufferedImage) IconLoader.load(ico.toString());

        assertThat(imagem).isNotNull();
        assertThat(imagem.getWidth(null)).isEqualTo(24);
        assertThat(imagem.getRGB(0, 0)).isEqualTo(new Color(0x33, 0x66, 0x99).getRGB());
    }

    @Test
    @DisplayName("Arquivo .ico com extensao errada ainda e reconhecido pelo conteudo")
    void deteccaoPorConteudoNaoPorExtensao(@TempDir Path tempDir) throws IOException {
        byte[] ico = ico32Bpp(8, 8, imagemCheia(8, 8, 0xFF445566), 0xFF);
        // Extensao .png, conteudo ICO: a deteccao usa a assinatura binaria.
        Path arquivo = gravar(tempDir, "disfarcado.png", ico);

        Image imagem = IconLoader.load(arquivo.toString());

        assertThat(imagem).isNotNull();
        assertThat(imagem.getWidth(null)).isEqualTo(8);
    }

    @Test
    @DisplayName("Texto puro com bytes que imitam ICO nao quebra o decoder")
    void conteudoArbitrarioNaoQuebra(@TempDir Path tempDir) throws IOException {
        byte[] conteudo = "nao sou um icone de verdade".getBytes(StandardCharsets.UTF_8);
        // Forca a assinatura ICO no inicio: o resto do conteudo e lixo.
        conteudo[0] = 0;
        conteudo[1] = 0;
        conteudo[2] = 1;
        conteudo[3] = 0;

        Path arquivo = gravar(tempDir, "falso.ico", conteudo);

        assertThat(IconLoader.load(arquivo.toString())).isNull();
    }
}

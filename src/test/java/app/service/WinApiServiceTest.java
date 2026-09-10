package app.service;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Testes do acesso a Win32 (user32.dll).
 *
 * <p>A interface {@code User32} e substituivel por um duble, o que permite
 * verificar o comportamento do servico (validacao de caminho, liberacao do
 * HICON, tratamento de falha) sem carregar a DLL nem abrir uma janela - algo
 * que nao seria possivel em ambiente headless.</p>
 */
class WinApiServiceTest {

    /** Duble que registra as chamadas em vez de chamar o Windows. */
    private static final class User32Falso implements WinApiService.User32 {

        private final List<String> chamadas = new ArrayList<>();
        private Pointer iconeDevolvido;
        private boolean falharAoCarregar;
        private boolean falharAoDestruir;

        @Override
        public int MessageBoxW(Pointer hWnd, String lpText, String lpCaption, int uType) {
            chamadas.add("MessageBoxW:" + lpCaption + "|" + uType);
            return 1;
        }

        @Override
        public Pointer LoadImageW(Pointer hInst, String lpszName, int uType,
                                  int cx, int cy, int fuLoad) {
            if (falharAoCarregar) {
                throw new UnsupportedOperationException("LoadImageW indisponivel");
            }
            chamadas.add("LoadImageW:" + lpszName + "|uType=" + uType
                    + "|cy=" + cy + "|fuLoad=" + fuLoad);
            return iconeDevolvido;
        }

        @Override
        public boolean DestroyIcon(Pointer hIcon) {
            if (falharAoDestruir) {
                throw new UnsupportedOperationException("DestroyIcon indisponivel");
            }
            chamadas.add("DestroyIcon:" + (hIcon != null));
            return true;
        }

        List<String> getChamadas() {
            return List.copyOf(chamadas);
        }
    }

    private final User32Falso falso = new User32Falso();

    @AfterEach
    void restaurar() {
        // Devolve o servico ao estado normal.
        WinApiService.useUser32ForTesting(null);
    }

    private void usarDuble() {
        WinApiService.useUser32ForTesting(falso);
    }

    @Nested
    @DisplayName("Caixa de mensagem")
    class CaixaDeMensagem {

        @Test
        @DisplayName("Repassa titulo, texto e flags para a API nativa")
        void repassaParametros() {
            usarDuble();

            int resultado = WinApiService.showMessageBox("Titulo", "Texto", 0x40);

            assertThat(resultado).isEqualTo(1);
            assertThat(falso.getChamadas()).containsExactly("MessageBoxW:Titulo|64");
        }

        @ParameterizedTest(name = "flags {0}")
        @ValueSource(ints = {0, 0x40, 0x10000, 0x40040})
        @DisplayName("Aceita as combinacoes de flags usadas pelo programa")
        void aceitaFlagsUsadas(int flags) {
            usarDuble();

            assertThat(WinApiService.showMessageBox("t", "x", flags)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Carregamento de icone")
    class CarregamentoDeIcone {

        @Test
        @DisplayName("Carrega o icone de um arquivo existente")
        void carregaIconeExistente(@TempDir Path tempDir) throws IOException {
            Path icone = tempDir.resolve("icone.ico");
            Files.write(icone, new byte[] {0, 0, 1, 0});
            falso.iconeDevolvido = new Memory(8);

            usarDuble();
            Pointer handle = WinApiService.loadIcon(icone.toString());

            assertThat(handle).isNotNull();
            assertThat(falso.getChamadas()).hasSize(1);
            assertThat(falso.getChamadas().get(0)).contains("LoadImageW:").contains("icone.ico");
        }

        @Test
        @DisplayName("Usa as flags de carregamento de arquivo e tamanho padrao")
        void usaFlagsCorretas(@TempDir Path tempDir) throws IOException {
            Path icone = tempDir.resolve("icone.ico");
            Files.write(icone, new byte[] {0, 0, 1, 0});
            falso.iconeDevolvido = new Memory(8);

            usarDuble();
            WinApiService.loadIcon(icone.toString());

            // LR_LOADFROMFILE (0x10) | LR_DEFAULTSIZE (0x40) = 0x50, IMAGE_ICON = 1.
            assertThat(falso.getChamadas().get(0)).contains("uType=1").contains("fuLoad=80");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("Caminho nulo ou em branco devolve null sem chamar o Windows")
        void caminhoInvalidoDevolveNull(String caminho) {
            usarDuble();

            assertThat(WinApiService.loadIcon(caminho)).isNull();
            assertThat(falso.getChamadas()).isEmpty();
        }

        @Test
        @DisplayName("Arquivo inexistente devolve null sem chamar o Windows")
        void arquivoInexistenteDevolveNull(@TempDir Path tempDir) {
            usarDuble();

            assertThat(WinApiService.loadIcon(tempDir.resolve("nao-existe.ico").toString()))
                    .isNull();
            assertThat(falso.getChamadas()).isEmpty();
        }

        @Test
        @DisplayName("Diretorio no lugar de arquivo devolve null")
        void diretorioDevolveNull(@TempDir Path tempDir) {
            usarDuble();

            assertThat(WinApiService.loadIcon(tempDir.toString())).isNull();
            assertThat(falso.getChamadas()).isEmpty();
        }

        @Test
        @DisplayName("Caminho relativo e resolvido a partir do diretorio de execucao")
        void caminhoRelativoEhResolvido() {
            usarDuble();

            // O arquivo nao existe, mas o caminho precisa ter sido absolutizado
            // antes de chegar ao Windows (que nao resolve relativo sozinho).
            assertThat(WinApiService.loadIcon("nao-existe.ico")).isNull();
            assertThat(falso.getChamadas()).isEmpty();
        }

        @Test
        @DisplayName("Falha da API nativa devolve null em vez de propagar excecao")
        void falhaDaApiDevolveNull(@TempDir Path tempDir) throws IOException {
            Path icone = tempDir.resolve("icone.ico");
            Files.write(icone, new byte[] {0, 0, 1, 0});
            falso.falharAoCarregar = true;

            usarDuble();

            assertThat(WinApiService.loadIcon(icone.toString())).isNull();
        }

        @Test
        @DisplayName("Caminho com espacos nas pontas e normalizado")
        void caminhoComEspacosEhNormalizado(@TempDir Path tempDir) throws IOException {
            Path icone = tempDir.resolve("icone.ico");
            Files.write(icone, new byte[] {0, 0, 1, 0});
            falso.iconeDevolvido = new Memory(8);

            usarDuble();

            assertThat(WinApiService.loadIcon("  " + icone + "  ")).isNotNull();
        }
    }

    @Nested
    @DisplayName("Liberacao de icone")
    class LiberacaoDeIcone {

        @Test
        @DisplayName("Libera o HICON informado")
        void liberaHandle() {
            usarDuble();

            WinApiService.destroyIcon(new Memory(8));

            assertThat(falso.getChamadas()).containsExactly("DestroyIcon:true");
        }

        @Test
        @DisplayName("Handle nulo nao chama o Windows")
        void handleNuloNaoChama() {
            usarDuble();

            WinApiService.destroyIcon(null);

            assertThat(falso.getChamadas()).isEmpty();
        }

        @Test
        @DisplayName("Falha ao liberar nao propaga excecao")
        void falhaAoLiberarNaoPropaga() {
            usarDuble();
            falso.falharAoDestruir = true;

            // O icone e um recurso acessorio; falhar ao liberar nao pode
            // interromper o encerramento do programa.
            assertThatCode(() -> WinApiService.destroyIcon(new Memory(8)))
                    .doesNotThrowAnyException();
        }
    }
}

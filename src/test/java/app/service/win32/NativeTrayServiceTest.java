package app.service.win32;

import app.config.AppConfig;
import app.service.SettingsService;
import app.service.TrayIconService;
import app.testutil.StubGistLoader;
import app.testutil.TestConfigs;
import app.utils.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes da bandeja nativa (Win32 via JNA).
 *
 * <p>Assim como no {@link app.service.TrayIconServiceTest}, os testes cobrem o
 * modelo puro do menu e o mapeamento de comandos - sem carregar as DLLs nem
 * abrir janela, o que nao seria possivel em ambiente headless.</p>
 */
class NativeTrayServiceTest {

    private static SettingsService settingsEm(Path tempDir) {
        AppConfig config = TestConfigs.build("C:\\SpedECF\\response.varfile",
                "https://www.gov.br/receitafederal/ecf", "rfb_subheader", 6, "EcfNotificador.ico");
        return new SettingsService(new StubGistLoader(config),
                tempDir.resolve("config").resolve("appSettings.json"));
    }

    @Nested
    @DisplayName("Paridade com o modelo Swing")
    class ParidadeDoMenu {

        @Test
        @DisplayName("O menu nativo tem os mesmos itens e a mesma ordem do Swing")
        void menuTemMesmaOrdem(@TempDir Path tempDir) {
            NativeTrayService nativo = new NativeTrayService(settingsEm(tempDir));
            TrayIconService swing = new TrayIconService(settingsEm(tempDir));

            List<TrayIconService.MenuEntry> menuNativo = nativo.buildMenuModel();
            List<TrayIconService.MenuEntry> menuSwing = swing.buildMenuModel();

            assertThat(menuNativo).hasSize(4);
            for (int i = 0; i < menuNativo.size(); i++) {
                assertThat(menuNativo.get(i).label())
                        .isEqualTo(menuSwing.get(i).label());
                assertThat(menuNativo.get(i).isSeparator())
                        .isEqualTo(menuSwing.get(i).isSeparator());
            }
        }

        @Test
        @DisplayName("As acoes registradas chegam aos itens correspondentes")
        void acoesChegamAosItens(@TempDir Path tempDir) {
            NativeTrayService servico = new NativeTrayService(settingsEm(tempDir));
            AtomicInteger verificacoes = new AtomicInteger();
            AtomicInteger configuracoes = new AtomicInteger();
            AtomicInteger saidas = new AtomicInteger();

            servico.addCheckNowAction(verificacoes::incrementAndGet);
            servico.addSettingsAction(configuracoes::incrementAndGet);
            servico.addExitAction(saidas::incrementAndGet);

            List<TrayIconService.MenuEntry> menu = servico.buildMenuModel();
            menu.get(0).action().run();
            menu.get(1).action().run();
            menu.get(3).action().run();

            assertThat(verificacoes.get()).isEqualTo(1);
            assertThat(configuracoes.get()).isEqualTo(1);
            assertThat(saidas.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("Os ids de comando nao colidem entre os itens visiveis")
        void idsDeComandoNaoColidem(@TempDir Path tempDir) {
            NativeTrayService servico = new NativeTrayService(settingsEm(tempDir));

            List<TrayIconService.MenuEntry> menu = servico.buildMenuModel();
            List<Integer> ids = new java.util.ArrayList<>();
            for (int i = 0; i < menu.size(); i++) {
                if (!menu.get(i).isSeparator()) {
                    ids.add(NativeTrayService.commandIdFor(i));
                }
            }

            assertThat(ids).doesNotHaveDuplicates();
            assertThat(ids).allMatch(id -> id >= User32Ui.MENU_ID_BASE);
        }
    }

    @Nested
    @DisplayName("Layout das estruturas JNA")
    class EstruturasJna {

        @Test
        @DisplayName("NOTIFYICONDATAW expoe o tooltip de 128 WCHAR e tamanho valido")
        void notificacaoTemTooltip() {
            Shell32.NOTIFYICONDATAW nid = new Shell32.NOTIFYICONDATAW();

            assertThat(nid.szTip).hasSize(128);
            assertThat(nid.size()).isPositive();
            assertThat(nid.size() % 2).isZero();
        }

        @Test
        @DisplayName("MSG e POINT tem tamanho positivo")
        void estruturasTemTamanho() {
            assertThat(new User32Ui.MSG().size()).isPositive();
            assertThat(new User32Ui.POINT().size()).isPositive();
            assertThat(new User32Ui.WNDCLASSEXW().size()).isPositive();
        }

        @Test
        @DisplayName("A classe de janela registra o procedimento de janela")
        void classeTemProcedimento() {
            User32Ui.WNDCLASSEXW wndClass = new User32Ui.WNDCLASSEXW();
            wndClass.lpfnWndProc = new User32Ui.WindowProc() {
                @Override
                public com.sun.jna.Pointer callback(com.sun.jna.Pointer hWnd, int uMsg,
                                                    com.sun.jna.Pointer wParam, com.sun.jna.Pointer lParam) {
                    return null;
                }
            };

            assertThat(wndClass.lpfnWndProc).isNotNull();
        }
    }
}

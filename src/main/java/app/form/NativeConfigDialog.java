package app.form;

import app.service.SettingsService;
import app.service.win32.User32Ui;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tela de Configuracoes nativa (Win32 via JNA).
 *
 * <p>Substitui o {@link ConfigForm} (Swing) quando o programa roda como
 * binario nativo (GraalVM), onde o Swing nao funciona. A janela e modal: o
 * loop de mensagens roda na thread chamadora ate o usuario Salvar ou Cancelar.</p>
 */
public final class NativeConfigDialog {

    private static final Logger log = LoggerFactory.getLogger(NativeConfigDialog.class);

    private static final String WINDOW_CLASS = "EcfNotificadorConfigDialog";
    private static final String TITLE = "Configura\u00e7\u00f5es - ECF Notificador";
    private static final String LABEL = "Intervalo de verifica\u00e7\u00e3o (horas):";

    private static final int WM_CLOSE = 0x0010;
    private static final int WS_EX_CONTROLPARENT = 0x00010000;

    private static final int MIN_HOURS = 1;
    private static final int MAX_HOURS = 168;

    private final SettingsService settings;
    private final DialogProc dialogProc = new DialogProc();
    private Pointer hwnd;
    private Pointer editHwnd;

    public NativeConfigDialog(SettingsService settings) {
        this.settings = settings;
    }

    /** Exibe o dialogo de forma modal. */
    public void show() {
        try {
            registerWindowClass();
            createWindow();
            createControls();
            showWindow();
            runModalLoop();
        } catch (Throwable t) {
            log.warn("Nao foi possivel exibir a tela de configuracoes nativa: {}", t.getMessage());
        }
    }

    private void registerWindowClass() {
        User32Ui.WNDCLASSEXW wndClass = new User32Ui.WNDCLASSEXW();
        wndClass.cbSize = wndClass.size();
        wndClass.lpfnWndProc = dialogProc;
        wndClass.hInstance = User32Ui.Kernel32.INSTANCE.GetModuleHandleW(null);
        wndClass.lpszClassName = WINDOW_CLASS;
        User32Ui.User32.INSTANCE.RegisterClassExW(wndClass);
    }

    private void createWindow() {
        Pointer hInstance = User32Ui.Kernel32.INSTANCE.GetModuleHandleW(null);
        int style = User32Ui.WS_OVERLAPPED | User32Ui.WS_CAPTION | User32Ui.WS_SYSMENU;
        hwnd = User32Ui.User32.INSTANCE.CreateWindowExW(
                WS_EX_CONTROLPARENT, WINDOW_CLASS, TITLE, style,
                0, 0, 340, 150, null, null, hInstance, null);
        if (hwnd == null) {
            throw new IllegalStateException("Nao foi possivel criar a janela de configuracoes");
        }
    }

    private void createControls() {
        User32Ui.User32 user32 = User32Ui.User32.INSTANCE;
        Pointer hInstance = User32Ui.Kernel32.INSTANCE.GetModuleHandleW(null);

        // Rotulo.
        user32.CreateWindowExW(0, User32Ui.WC_STATIC, LABEL,
                User32Ui.WS_CHILD | User32Ui.WS_VISIBLE,
                12, 14, 220, 20, hwnd, null, hInstance, null);

        // Caixa de edicao numerica, preenchida com o intervalo atual.
        editHwnd = user32.CreateWindowExW(0, User32Ui.WC_EDIT,
                String.valueOf(settings.getCheckIntervalHours()),
                User32Ui.WS_CHILD | User32Ui.WS_VISIBLE | User32Ui.WS_BORDER
                        | User32Ui.WS_TABSTOP | User32Ui.ES_NUMBER
                        | User32Ui.ES_LEFT | User32Ui.ES_AUTOHSCROLL,
                12, 40, 80, 24, hwnd, new Pointer(User32Ui.IDC_EDIT_INTERVAL), hInstance, null);
        user32.SendMessageW(editHwnd, User32Ui.EM_SETLIMITTEXT, new Pointer(3), null);

        // Botoes Salvar e Cancelar.
        user32.CreateWindowExW(0, User32Ui.WC_BUTTON, "Salvar",
                User32Ui.WS_CHILD | User32Ui.WS_VISIBLE | User32Ui.WS_TABSTOP,
                12, 78, 90, 30, hwnd, new Pointer(User32Ui.IDC_BUTTON_SAVE), hInstance, null);
        user32.CreateWindowExW(0, User32Ui.WC_BUTTON, "Cancelar",
                User32Ui.WS_CHILD | User32Ui.WS_VISIBLE | User32Ui.WS_TABSTOP,
                112, 78, 90, 30, hwnd, new Pointer(User32Ui.IDC_BUTTON_CANCEL), hInstance, null);
    }

    private void showWindow() {
        User32Ui.User32 user32 = User32Ui.User32.INSTANCE;
        user32.ShowWindow(hwnd, User32Ui.SW_SHOW);
        user32.UpdateWindow(hwnd);
    }

    private void runModalLoop() {
        User32Ui.User32 user32 = User32Ui.User32.INSTANCE;
        User32Ui.MSG msg = new User32Ui.MSG();
        while (user32.GetMessageW(msg, null, 0, 0) > 0) {
            user32.TranslateMessage(msg);
            user32.DispatchMessageW(msg);
        }
    }

    private int readInterval() {
        if (editHwnd == null) {
            return settings.getCheckIntervalHours();
        }
        User32Ui.User32 user32 = User32Ui.User32.INSTANCE;
        int length = user32.GetWindowTextLengthW(editHwnd);
        if (length <= 0) {
            return settings.getCheckIntervalHours();
        }
        char[] buffer = new char[length + 1];
        user32.GetWindowTextW(editHwnd, buffer, length + 1);
        try {
            int value = Integer.parseInt(new String(buffer, 0, length).trim());
            return clamp(value);
        } catch (NumberFormatException e) {
            return settings.getCheckIntervalHours();
        }
    }

    private static int clamp(int value) {
        return Math.max(MIN_HOURS, Math.min(MAX_HOURS, value));
    }

    /** Procedimento de janela do dialogo. */
    private final class DialogProc implements User32Ui.WindowProc {
        @Override
        public Pointer callback(Pointer hWnd, int uMsg, Pointer wParam, Pointer lParam) {
            User32Ui.User32 user32 = User32Ui.User32.INSTANCE;
            if (uMsg == User32Ui.WM_COMMAND) {
                int controlId = (int) (Pointer.nativeValue(wParam) & 0xFFFFL);
                if (controlId == User32Ui.IDC_BUTTON_SAVE) {
                    settings.setCheckIntervalHours(readInterval());
                    user32.DestroyWindow(hWnd);
                    return null;
                }
                if (controlId == User32Ui.IDC_BUTTON_CANCEL) {
                    user32.DestroyWindow(hWnd);
                    return null;
                }
            } else if (uMsg == WM_CLOSE) {
                user32.DestroyWindow(hWnd);
                return null;
            } else if (uMsg == User32Ui.WM_DESTROY) {
                user32.PostQuitMessage(0);
                return null;
            }
            return user32.DefWindowProcW(hWnd, uMsg, wParam, lParam);
        }
    }
}

package app.service.win32;

import app.service.SettingsService;
import app.service.TrayIconService;
import app.service.WinApiService;
import app.utils.Message;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bandeja do sistema usando {@code Shell_NotifyIconW} (Win32 via JNA).
 *
 * <p>Substitui o {@code SystemTray} do AWT quando o programa roda como binario
 * nativo (GraalVM), onde o AWT nao funciona. Expõe a mesma API do
 * {@link TrayIconService} e reutiliza {@link TrayIconService.MenuEntry} como
 * modelo puro do menu, mantendo o contrato testavel sem abrir janela.</p>
 *
 * <p><b>Threading:</b> a janela oculta (message-only) e o loop de mensagens
 * precisam rodar na mesma thread - o Windows entrega as mensagens da janela na
 * fila da thread que a criou. Por isso {@link #show()} delega todo o trabalho
 * nativo para uma thread dedicada e retorna imediatamente.</p>
 */
public class NativeTrayService {

    private static final Logger log = LoggerFactory.getLogger(NativeTrayService.class);

    /** WM_APP + 1: mensagem de callback do icone (cliques na bandeja). */
    static final int TRAY_CALLBACK_MSG = User32Ui.WM_APP + 1;
    /** WM_APP + 2: pedido de encerramento da janela/loop. */
    static final int TRAY_QUIT_MSG = User32Ui.WM_APP + 2;

    private static final String WINDOW_CLASS = "EcfNotificadorTrayWindow";
    private static final int TRAY_ICON_ID = 1;

    private final SettingsService settings;

    private Runnable checkNowAction;
    private Runnable settingsAction;
    private Runnable exitAction;
    private Runnable doubleClickAction;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private WindowProcImpl windowProc;
    private Pointer hwnd;
    private Pointer trayIconHandle;
    private Thread loopThread;
    private final Map<Integer, Runnable> commandActions = new LinkedHashMap<>();

    public NativeTrayService(SettingsService settings) {
        this.settings = settings;
    }

    public void addCheckNowAction(Runnable action) {
        this.checkNowAction = action;
    }

    public void addSettingsAction(Runnable action) {
        this.settingsAction = action;
    }

    public void addExitAction(Runnable action) {
        this.exitAction = action;
    }

    public void addDoubleClickAction(Runnable action) {
        this.doubleClickAction = action;
    }

    /**
     * Monta o modelo do menu, na mesma ordem do {@link TrayIconService}: a
     * paridade e verificada por teste. Metodo puro, sem Win32.
     */
    public List<TrayIconService.MenuEntry> buildMenuModel() {
        List<TrayIconService.MenuEntry> entradas = new ArrayList<>();
        entradas.add(new TrayIconService.MenuEntry(Message.MENU_CHECK_NOW.getValue(), checkNowAction));
        entradas.add(new TrayIconService.MenuEntry(Message.MENU_SETTINGS.getValue(), settingsAction));
        entradas.add(TrayIconService.MenuEntry.separator());
        entradas.add(new TrayIconService.MenuEntry(Message.MENU_EXIT.getValue(), exitAction));
        return List.copyOf(entradas);
    }

    /**
     * Mapeia indice do modelo para id de comando do menu nativo. Separadores
     * nao recebem id (devem ser pulados pelo chamador). Visivel no pacote para
     * os testes validarem o mapeamento.
     */
    static int commandIdFor(int modelIndex) {
        return User32Ui.MENU_ID_BASE + modelIndex;
    }

    /** Inicia a bandeja em uma thread dedicada. */
    public void show() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        loopThread = new Thread(this::runMessageLoop, "ecf-tray-native");
        loopThread.setDaemon(true);
        loopThread.start();
    }

    /** Remove o icone e encerra o loop de mensagens. Idempotente. */
    public void remove() {
        if (!running.get()) {
            return;
        }
        running.set(false);
        if (hwnd != null) {
            User32Ui.User32.INSTANCE.SendMessageW(hwnd, TRAY_QUIT_MSG, null, null);
        }
    }

    private void runMessageLoop() {
        try {
            registerWindowClass();
            createMessageWindow();
            addTrayIcon();

            User32Ui.MSG msg = new User32Ui.MSG();
            while (running.get()) {
                int result = User32Ui.User32.INSTANCE.GetMessageW(msg, null, 0, 0);
                if (result <= 0) {
                    break;
                }
                User32Ui.User32.INSTANCE.TranslateMessage(msg);
                User32Ui.User32.INSTANCE.DispatchMessageW(msg);
            }
        } catch (Throwable t) {
            log.error("Falha ao iniciar a bandeja nativa: {}", t.getMessage());
        } finally {
            removeTrayIcon();
            destroyTrayIconHandle();
            hwnd = null;
            windowProc = null;
            running.set(false);
        }
    }

    private void registerWindowClass() {
        User32Ui.WNDCLASSEXW wndClass = new User32Ui.WNDCLASSEXW();
        wndClass.cbSize = wndClass.size();
        wndClass.lpfnWndProc = windowProc();
        wndClass.hInstance = User32Ui.Kernel32.INSTANCE.GetModuleHandleW(null);
        wndClass.lpszClassName = WINDOW_CLASS;
        User32Ui.User32.INSTANCE.RegisterClassExW(wndClass);
    }

    private WindowProcImpl windowProc() {
        if (windowProc == null) {
            windowProc = new WindowProcImpl();
        }
        return windowProc;
    }

    private void createMessageWindow() {
        Pointer hInstance = User32Ui.Kernel32.INSTANCE.GetModuleHandleW(null);
        hwnd = User32Ui.User32.INSTANCE.CreateWindowExW(
                0, WINDOW_CLASS, "",
                0, 0, 0, 0, 0,
                User32Ui.HWND_MESSAGE, null, hInstance, null);
        if (hwnd == null) {
            throw new IllegalStateException("Nao foi possivel criar a janela da bandeja");
        }
    }

    private void addTrayIcon() {
        trayIconHandle = WinApiService.loadIcon(settings.getIconPath());

        Shell32.NOTIFYICONDATAW nid = new Shell32.NOTIFYICONDATAW();
        nid.cbSize = nid.size();
        nid.hWnd = hwnd;
        nid.uID = TRAY_ICON_ID;
        nid.uFlags = Shell32.NIF_MESSAGE | Shell32.NIF_ICON | Shell32.NIF_TIP;
        nid.uCallbackMessage = TRAY_CALLBACK_MSG;
        nid.hIcon = trayIconHandle;
        nid.szTip = toCharArray(Message.TRAY_TITLE.getValue());
        nid.write();

        boolean added = Shell32.Api.INSTANCE.Shell_NotifyIconW(Shell32.NIM_ADD, nid);
        if (!added) {
            log.warn("Shell_NotifyIconW(NIM_ADD) falhou.");
        }
    }

    private void removeTrayIcon() {
        if (hwnd == null) {
            return;
        }
        Shell32.NOTIFYICONDATAW nid = new Shell32.NOTIFYICONDATAW();
        nid.cbSize = nid.size();
        nid.hWnd = hwnd;
        nid.uID = TRAY_ICON_ID;
        nid.write();
        Shell32.Api.INSTANCE.Shell_NotifyIconW(Shell32.NIM_DELETE, nid);
    }

    private void destroyTrayIconHandle() {
        WinApiService.destroyIcon(trayIconHandle);
        trayIconHandle = null;
    }

    private void rebuildCommandActions() {
        commandActions.clear();
        int index = 0;
        for (TrayIconService.MenuEntry entry : buildMenuModel()) {
            if (!entry.isSeparator()) {
                commandActions.put(commandIdFor(index), entry.action());
            }
            index++;
        }
    }

    private void showContextMenu() {
        User32Ui.User32 user32 = User32Ui.User32.INSTANCE;
        User32Ui.POINT cursor = new User32Ui.POINT();
        if (!user32.GetCursorPos(cursor)) {
            return;
        }

        Pointer menu = user32.CreatePopupMenu();
        if (menu == null) {
            return;
        }

        rebuildCommandActions();

        int index = 0;
        for (TrayIconService.MenuEntry entry : buildMenuModel()) {
            if (entry.isSeparator()) {
                user32.AppendMenuW(menu, User32Ui.MF_SEPARATOR, 0, null);
            } else {
                user32.AppendMenuW(menu, User32Ui.MF_STRING, commandIdFor(index), entry.label());
            }
            index++;
        }

        // Sem SetForegroundWindow o menu pode nao fechar ao clicar fora dele.
        user32.SetForegroundWindow(hwnd);
        user32.TrackPopupMenu(menu,
                User32Ui.TPM_RIGHTBUTTON | User32Ui.TPM_LEFTALIGN | User32Ui.TPM_TOPALIGN,
                cursor.x, cursor.y, 0, hwnd, null);
        user32.DestroyMenu(menu);
    }

    private void dispatchCommand(int commandId) {
        Runnable action = commandActions.get(commandId);
        if (action != null) {
            action.run();
        }
    }

    private void runAction(Runnable action) {
        if (action != null) {
            action.run();
        }
    }

    private static char[] toCharArray(String text) {
        return text == null ? new char[0] : text.toCharArray();
    }

    /** Procedimento de janela da bandeja. */
    private final class WindowProcImpl implements User32Ui.WindowProc {
        @Override
        public Pointer callback(Pointer hWnd, int uMsg, Pointer wParam, Pointer lParam) {
            if (uMsg == TRAY_CALLBACK_MSG) {
                int mouseEvent = lowWord(lParam);
                if (mouseEvent == User32Ui.WM_LBUTTONDBLCLK) {
                    runAction(doubleClickAction);
                    return null;
                }
                if (mouseEvent == User32Ui.WM_RBUTTONUP) {
                    showContextMenu();
                    return null;
                }
            } else if (uMsg == User32Ui.WM_COMMAND) {
                dispatchCommand(lowWord(wParam));
                return null;
            } else if (uMsg == TRAY_QUIT_MSG) {
                User32Ui.User32.INSTANCE.DestroyWindow(hWnd);
                return null;
            } else if (uMsg == User32Ui.WM_DESTROY) {
                User32Ui.User32.INSTANCE.PostQuitMessage(0);
                return null;
            }
            return User32Ui.User32.INSTANCE.DefWindowProcW(hWnd, uMsg, wParam, lParam);
        }

        private int lowWord(Pointer value) {
            return (int) (Pointer.nativeValue(value) & 0xFFFFL);
        }
    }
}

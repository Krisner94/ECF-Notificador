package app.service.win32;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.StdCallLibrary.StdCallCallback;
import com.sun.jna.win32.W32APIOptions;

/**
 * Funcoes de user32.dll e kernel32.dll usadas pela UI nativa (bandeja e
 * dialogo de configuracoes).
 *
 * <p>Separadas de {@code WinApiService.User32} para nao alterar a interface ja
 * substituida pelos testes existentes. Estas funcoes sao usadas apenas pelo
 * caminho nativo (GraalVM), nunca pelo caminho Swing do JAR.</p>
 */
public final class User32Ui {

    private User32Ui() {
    }

    /** Mensagens de janela usadas pelo programa. */
    public static final int WM_COMMAND = 0x0111;
    public static final int WM_DESTROY = 0x0002;
    public static final int WM_LBUTTONDBLCLK = 0x0203;
    public static final int WM_RBUTTONUP = 0x0205;
    public static final int WM_APP = 0x8000;

    /** Estilos de janela. */
    public static final int WS_OVERLAPPED = 0x00000000;
    public static final int WS_CAPTION = 0x00C00000;
    public static final int WS_SYSMENU = 0x00080000;
    public static final int WS_VISIBLE = 0x10000000;
    public static final int WS_CHILD = 0x40000000;
    public static final int WS_TABSTOP = 0x00010000;
    public static final int WS_BORDER = 0x00800000;

    /** Janela apenas de mensagens (sem superficie). */
    public static final Pointer HWND_MESSAGE = new Pointer(-3L);

    /** Estilos de controle. */
    public static final int ES_NUMBER = 0x2000;
    public static final int ES_AUTOHSCROLL = 0x0080;
    public static final int ES_LEFT = 0x0000;

    /** Classes de controle padrao do Windows. */
    public static final String WC_STATIC = "STATIC";
    public static final String WC_EDIT = "EDIT";
    public static final String WC_BUTTON = "BUTTON";

    /** Estilos de menu. */
    public static final int MF_STRING = 0x00000000;
    public static final int MF_SEPARATOR = 0x00000800;

    /** Flags do TrackPopupMenu. */
    public static final int TPM_RIGHTBUTTON = 0x0002;
    public static final int TPM_TOPALIGN = 0x0000;
    public static final int TPM_LEFTALIGN = 0x0000;

    /** Comandos de ShowWindow. */
    public static final int SW_SHOW = 5;

    /** Limite da caixa de edicao do intervalo (168 horas). */
    public static final int EM_SETLIMITTEXT = 0x00C5;

    /** IDs dos controles do dialogo de configuracoes. */
    public static final int IDC_EDIT_INTERVAL = 1001;
    public static final int IDC_BUTTON_SAVE = 1002;
    public static final int IDC_BUTTON_CANCEL = 1003;

    /** ids dos itens de menu da bandeja (base + indice do modelo). */
    public static final int MENU_ID_BASE = 2000;

    /**
     * Procedimento de janela. O ponteiro retornado e o LRESULT (DefWindowProc
     * devolve o valor processado); o objeto do callback precisa permanecer
     * fortemente referenciado enquanto a janela existir.
     */
    public interface WindowProc extends StdCallCallback {
        Pointer callback(Pointer hWnd, int uMsg, Pointer wParam, Pointer lParam);
    }

    public interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class, W32APIOptions.UNICODE_OPTIONS);

        short RegisterClassExW(WNDCLASSEXW lpwcx);

        Pointer CreateWindowExW(int dwExStyle, String lpClassName, String lpWindowName,
                                int dwStyle, int x, int y, int nWidth, int nHeight,
                                Pointer hWndParent, Pointer hMenu, Pointer hInstance,
                                Pointer lpParam);

        Pointer DefWindowProcW(Pointer hWnd, int uMsg, Pointer wParam, Pointer lParam);

        boolean DestroyWindow(Pointer hWnd);

        int GetMessageW(MSG lpMsg, Pointer hWnd, int wMsgFilterMin, int wMsgFilterMax);

        boolean TranslateMessage(MSG lpMsg);

        Pointer DispatchMessageW(MSG lpMsg);

        void PostQuitMessage(int nExitCode);

        Pointer CreatePopupMenu();

        boolean AppendMenuW(Pointer hMenu, int uFlags, int uIDNewItem, String lpNewItem);

        boolean DestroyMenu(Pointer hMenu);

        boolean TrackPopupMenu(Pointer hMenu, int uFlags, int x, int y, int nReserved,
                               Pointer hWnd, Pointer prcRect);

        boolean SetForegroundWindow(Pointer hWnd);

        boolean GetCursorPos(POINT lpPoint);

        boolean ShowWindow(Pointer hWnd, int nCmdShow);

        boolean UpdateWindow(Pointer hWnd);

        boolean SetWindowTextW(Pointer hWnd, String lpString);

        int GetWindowTextLengthW(Pointer hWnd);

        int GetWindowTextW(Pointer hWnd, char[] lpString, int nMaxCount);

        Pointer SendMessageW(Pointer hWnd, int msg, Pointer wParam, Pointer lParam);
    }

    public interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class, W32APIOptions.UNICODE_OPTIONS);

        Pointer GetModuleHandleW(String lpModuleName);
    }

    /** POINT - coordenadas da tela. */
    @Structure.FieldOrder({"x", "y"})
    public static class POINT extends Structure {
        public int x;
        public int y;
    }

    /**
     * MSG - mensagem da fila do Windows.
     *
     * <p>O {@code pt} (POINT) e inlinado como {@code ptX}/{@code ptY} porque um
     * campo {@link Structure} aninhado e tratado pelo JNA como ponteiro, o que
     * desalinharia o layout x64 da estrutura.</p>
     */
    @Structure.FieldOrder({"hwnd", "message", "wParam", "lParam", "time", "ptX", "ptY", "lPrivate"})
    public static class MSG extends Structure {
        public Pointer hwnd;
        public int message;
        public Pointer wParam;
        public Pointer lParam;
        public int time;
        public int ptX;
        public int ptY;
        public int lPrivate;
    }

    /** WNDCLASSEXW - registro da classe de janela. */
    @Structure.FieldOrder({"cbSize", "style", "lpfnWndProc", "cbClsExtra", "cbWndExtra",
            "hInstance", "hIcon", "hCursor", "hbrBackground", "lpszMenuName",
            "lpszClassName", "hIconSm"})
    public static class WNDCLASSEXW extends Structure {
        public int cbSize;
        public int style;
        public WindowProc lpfnWndProc;
        public int cbClsExtra;
        public int cbWndExtra;
        public Pointer hInstance;
        public Pointer hIcon;
        public Pointer hCursor;
        public Pointer hbrBackground;
        public String lpszMenuName;
        public String lpszClassName;
        public Pointer hIconSm;
    }
}

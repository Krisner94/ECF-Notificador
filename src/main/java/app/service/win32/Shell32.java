package app.service.win32;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * Acesso a shell32.dll para a bandeja do sistema.
 *
 * <p>O programa usa {@code Shell_NotifyIconW} em vez de {@code SystemTray}
 * porque o AWT nao funciona em binario nativo (GraalVM Native Image). A
 * estrutura e declarada no layout minimo (V1, ate {@code szTip}): e o
 * suficiente para adicionar/remover o icone com tooltip e mensagem de
 * callback, sem os campos de balao que o programa nao usa.</p>
 */
public final class Shell32 {

    private Shell32() {
    }

    /** NIM_ADD - adiciona o icone a bandeja. */
    public static final int NIM_ADD = 0x00000000;
    /** NIM_MODIFY - altera o icone/tooltip. */
    public static final int NIM_MODIFY = 0x00000001;
    /** NIM_DELETE - remove o icone da bandeja. */
    public static final int NIM_DELETE = 0x00000002;

    /** NIF_MESSAGE - habilita a mensagem de callback. */
    public static final int NIF_MESSAGE = 0x00000001;
    /** NIF_ICON - habilita o icone. */
    public static final int NIF_ICON = 0x00000002;
    /** NIF_TIP - habilita o tooltip. */
    public static final int NIF_TIP = 0x00000004;

    /**
     * Funcoes de shell32.dll utilizadas pelo programa. A DLL so e carregada
     * quando {@link #INSTANCE} e acessado - nunca ao instanciar a estrutura
     * {@link NOTIFYICONDATAW}, o que permite testar o layout em Linux.
     */
    public interface Api extends StdCallLibrary {
        Api INSTANCE = Native.load("shell32", Api.class, W32APIOptions.UNICODE_OPTIONS);

        /**
         * Adiciona, altera ou remove um icone na bandeja do sistema.
         *
         * @param dwMessage {@link #NIM_ADD}, {@link #NIM_MODIFY} ou {@link #NIM_DELETE}.
         * @param lpData    estrutura {@link NOTIFYICONDATAW} preenchida.
         * @return {@code true} em caso de sucesso.
         */
        boolean Shell_NotifyIconW(int dwMessage, NOTIFYICONDATAW lpData);

        /**
         * Abre um arquivo/URL no manipulador padrao do sistema (ex.: navegador).
         * Devolve um valor maior que 32 em caso de sucesso.
         */
        Pointer ShellExecuteW(Pointer hwnd, String lpOperation, String lpFile,
                              String lpParameters, String lpDirectory, int nShowCmd);
    }

    /**
     * NOTIFYICONDATAW no layout minimo (V1). Campos alem de {@code szTip}
     * (balao, guid, versao) sao omitidos de proposito; o {@code cbSize}
     * resultante informa ao shell o tamanho V1, que cobre tudo o que o
     * programa usa.
     */
    @Structure.FieldOrder({"cbSize", "hWnd", "uID", "uFlags", "uCallbackMessage", "hIcon", "szTip"})
    public static class NOTIFYICONDATAW extends Structure {
        public int cbSize;
        public Pointer hWnd;
        public int uID;
        public int uFlags;
        public int uCallbackMessage;
        public Pointer hIcon;
        public char[] szTip = new char[128];
    }
}

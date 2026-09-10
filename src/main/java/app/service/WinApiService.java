package app.service;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Chamadas a user32.dll usadas pelo programa.
 *
 * <p>A interface {@code User32} e publica de proposito: e o ponto de
 * substituicao nos testes, que assim validam o fluxo de icone sem carregar a DLL
 * nem abrir janela.</p>
 */
public final class WinApiService {

    private static final Logger log = LoggerFactory.getLogger(WinApiService.class);

    private static final int IMAGE_ICON = 1;
    private static final int LR_LOADFROMFILE = 0x00000010;
    private static final int LR_DEFAULTSIZE = 0x00000040;

    private static User32 user32 = null;

    /** Funcoes de user32.dll utilizadas pelo programa. */
    public interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class, W32APIOptions.UNICODE_OPTIONS);

        int MessageBoxW(Pointer hWnd, String lpText, String lpCaption, int uType);

        Pointer LoadImageW(Pointer hInst, String lpszName, int uType, int cx, int cy, int fuLoad);

        boolean DestroyIcon(Pointer hIcon);
    }

    private WinApiService() {
    }

    /**
     * Define a implementacao de user32 usada pelo servico. Destinado aos testes;
     * em producao a instancia real vem de {@link User32#INSTANCE}.
     */
    static void useUser32ForTesting(User32 implementacao) {
        user32 = implementacao;
    }
    private static User32 user32() {
        return user32 != null ? user32 : User32.INSTANCE;
    }

    public static int showMessageBox(String title, String text, int flags) {
        return user32().MessageBoxW(null, text, title, flags);
    }

    private static Pointer loadIconHandle(String iconPath) {
        if (iconPath == null || iconPath.isBlank()) {
            return null;
        }

        Path path;
        try {
            path = Paths.get(iconPath.trim());
        } catch (RuntimeException e) {
            return null;
        }

        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(path);
        }
        if (!Files.isRegularFile(path)) {
            return null;
        }

        try {
            return user32().LoadImageW(
                    null,
                    path.toAbsolutePath().toString(),
                    IMAGE_ICON,
                    0,
                    0,
                    LR_LOADFROMFILE | LR_DEFAULTSIZE);
        } catch (RuntimeException e) {
            log.warn("Nao foi possivel carregar o icone '{}': {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * Carrega o HICON do arquivo informado. Devolve {@code null} quando o
     * caminho e invalido, o arquivo nao existe ou a API falha. O chamador e
     * responsavel por liberar com {@link #destroyIcon(Pointer)}.
     */
    public static Pointer loadIcon(String iconPath) {
        return loadIconHandle(iconPath);
    }

    /**
     * Libera um HICON. Falha ao liberar nao propaga excecao: o icone e um recurso
     * acessorio e nao pode interromper o encerramento do programa.
     */
    public static void destroyIcon(Pointer hIcon) {
        if (hIcon == null) {
            return;
        }
        try {
            user32().DestroyIcon(hIcon);
        } catch (RuntimeException e) {
            log.debug("Nao foi possivel liberar o HICON: {}", e.getMessage());
        }
    }
}

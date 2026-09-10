package app.utils;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;

/**
 * Aplica o tema padrao do Windows as janelas do programa.
 *
 * <p>As cores vem de {@code GetSysColor} - as mesmas que o Windows usa nos
 * dialogos nativos - para a janela de Configuracoes ficar identica ao popup de
 * atualizacao.</p>
 *
 * <p>Nao usa o registro de tema (AppsUseLightTheme): os dialogos classicos do
 * comctl32 nao tem modo escuro, entao o registro deixaria a janela escura e o
 * popup claro.</p>
 *
 * Chamar uma unica vez, antes de criar qualquer componente Swing.
 */
public final class ThemeService {

    private static final Logger log = LoggerFactory.getLogger(ThemeService.class);

    /** Fonte da interface do Windows. */
    private static final String WINDOWS_FONT = "Segoe UI";
    private static final String FALLBACK_FONT = "SansSerif";
    private static final int BASE_FONT_SIZE = 13;

    /** Limiar de brilho para considerar uma cor "escura". */
    private static final int DARK_BRIGHTNESS_THRESHOLD = 128;

    /** Indices de cores do sistema (winuser.h). */
    private static final int COLOR_WINDOW = 5;
    private static final int COLOR_BTNFACE = 15;

    public interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS);

        int GetSysColor(int nIndex);
    }

    private ThemeService() {
    }

    /** Instala o Look and Feel; sem FlatLaf no classpath a aparencia padrao e mantida. */
    public static void install() {
        try {
            Color dialogFace = readSystemColor(COLOR_BTNFACE, new Color(240, 240, 240));
            boolean dark = brightness(dialogFace) < DARK_BRIGHTNESS_THRESHOLD;

            FlatLaf.setPreferredFontFamily(resolveFontFamily());
            FlatLaf lookAndFeel = dark ? new FlatDarkLaf() : new FlatLightLaf();

            if (!FlatLaf.setup(lookAndFeel)) {
                log.warn("FlatLaf nao pode ser aplicado nesta plataforma.");
                return;
            }

            applyBaseFont();

            // Tema claro: adota as cores dos dialogos nativos para a janela
            // ficar identica ao popup.
            if (!dark) {
                applyNativeDialogColors();
            }

            enableNativeWindowDecorations();

            log.info("Tema aplicado conforme o padrao do Windows ({}).", dark ? "escuro" : "claro");
        } catch (Throwable t) {
            // Inclui NoClassDefFoundError, caso o FlatLaf falte no classpath.
            log.warn("Nao foi possivel aplicar o tema: {}", t.getMessage());
        }
    }

    private static Color readSystemColor(int index, Color fallback) {
        try {
            int value = User32.INSTANCE.GetSysColor(index);
            Color color = new Color(value & 0xFFFFFF);
            if (!isValid(color)) {
                return fallback;
            }
            return color;
        } catch (Throwable t) {
            log.debug("Nao foi possivel ler a cor de sistema {}: {}", index, t.getMessage());
            return fallback;
        }
    }

    private static boolean isValid(Color color) {
        // O Windows usa preto puro quando a leitura falha; nesse caso descartamos.
        return !(color.getRed() == 0 && color.getGreen() == 0 && color.getBlue() == 0);
    }

    /** Aplica as cores dos dialogos nativos aos componentes Swing. */
    private static void applyNativeDialogColors() {
        try {
            Color dialogFace = readSystemColor(COLOR_BTNFACE, null);
            Color windowBackground = readSystemColor(COLOR_WINDOW, null);

            if (dialogFace != null) {
                UIManager.put("Panel.background", dialogFace);
                UIManager.put("OptionPane.background", dialogFace);
                UIManager.put("Button.background", dialogFace);
                UIManager.put("Dialog.background", dialogFace);
            }
            if (windowBackground != null) {
                UIManager.put("TextField.background", windowBackground);
                UIManager.put("FormattedTextField.background", windowBackground);
            }
        } catch (Throwable t) {
            log.debug("Nao foi possivel aplicar as cores nativas: {}", t.getMessage());
        }
    }

    private static int brightness(Color color) {
        return (int) Math.round(0.299 * color.getRed()
                + 0.587 * color.getGreen()
                + 0.114 * color.getBlue());
    }

    /** Usa a fonte da interface do Windows quando ela existir. */
    private static String resolveFontFamily() {
        try {
            String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames();
            if (Arrays.asList(available).contains(WINDOWS_FONT)) {
                return WINDOWS_FONT;
            }
        } catch (Throwable t) {
            log.debug("Nao foi possivel listar as fontes do sistema: {}", t.getMessage());
        }
        return FALLBACK_FONT;
    }

    /** Aplica o tamanho de fonte usado pelos aplicativos do Windows. */
    private static void applyBaseFont() {
        Font base = UIManager.getFont("defaultFont");
        if (base != null) {
            UIManager.put("defaultFont", base.deriveFont((float) BASE_FONT_SIZE));
        }
    }

    /** Habilita a decoracao nativa da janela (barra de titulo do Windows). */
    private static void enableNativeWindowDecorations() {
        try {
            if (FlatLaf.supportsNativeWindowDecorations()) {
                FlatLaf.setUseNativeWindowDecorations(true);
            }
        } catch (Throwable t) {
            log.debug("Decoracao nativa de janela indisponivel: {}", t.getMessage());
        }
    }
}

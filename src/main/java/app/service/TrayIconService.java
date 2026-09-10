package app.service;

import app.utils.IconLoader;
import app.utils.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.util.ArrayList;
import java.util.List;

/**
 * Icone na bandeja do sistema e menu de contexto.
 *
 * <p>O menu e montado a partir de um modelo puro ({@link MenuEntry}) para poder
 * ser testado sem instanciar componentes AWT, que exigiriam ambiente grafico.</p>
 */
public class TrayIconService {

    private static final Logger log = LoggerFactory.getLogger(TrayIconService.class);

    private static final int DEFAULT_ICON_SIZE = 16;

    /**
     * Um item do menu de contexto.
     *
     * @param label  texto exibido.
     * @param action acao disparada; {@code null} representa um separador.
     */
    public record MenuEntry(String label, Runnable action) {

        public boolean isSeparator() {
            return label == null && action == null;
        }

        public static MenuEntry separator() {
            return new MenuEntry(null, null);
        }
    }

    private final SettingsService settings;
    private TrayIcon trayIcon;

    private Runnable checkNowAction;
    private Runnable settingsAction;
    private Runnable exitAction;
    private Runnable doubleClickAction;

    public TrayIconService(SettingsService settings) {
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

    /**
     * Registra a acao executada no duplo clique do icone da bandeja.
     * Por padrao, dispara a mesma verificacao do menu "Verificar agora".
     */
    public void addDoubleClickAction(Runnable action) {
        this.doubleClickAction = action;
    }

    /**
     * Monta o modelo do menu de contexto, na ordem exibida ao usuario.
     *
     * <p>Metodo puro (sem AWT): e o que os testes verificam. A ordem e
     * {@code Verificar agora}, {@code Configurações}, separador, {@code Sair} -
     * com o separador isolando a acao destrutiva das demais.</p>
     */
    public List<MenuEntry> buildMenuModel() {
        List<MenuEntry> entradas = new ArrayList<>();
        entradas.add(new MenuEntry(Message.MENU_CHECK_NOW.getValue(), checkNowAction));
        entradas.add(new MenuEntry(Message.MENU_SETTINGS.getValue(), settingsAction));
        entradas.add(MenuEntry.separator());
        entradas.add(new MenuEntry(Message.MENU_EXIT.getValue(), exitAction));
        return List.copyOf(entradas);
    }

    public void show() {
        if (!SystemTray.isSupported()) {
            log.error("SystemTray nao e suportado pelo sistema operacional.");
            return;
        }

        try {
            SystemTray tray = SystemTray.getSystemTray();
            Image image = loadIcon();
            trayIcon = new TrayIcon(image, Message.TRAY_TITLE.getValue(), buildMenu());
            trayIcon.setImageAutoSize(true);

            // Duplo clique no icone dispara uma verificacao imediata.
            trayIcon.addActionListener(e -> {
                log.info("Duplo clique no icone da bandeja.");
                runAction(doubleClickAction);
            });

            tray.add(trayIcon);
        } catch (AWTException e) {
            log.error("Erro ao inicializar o TrayIcon", e);
        } catch (RuntimeException e) {
            // HeadlessException e afins: a bandeja e opcional, o programa nao
            // pode morrer por nao conseguir exibi-la.
            log.error("Bandeja do sistema indisponivel: {}", e.getMessage());
        }
    }

    public void remove() {
        if (trayIcon == null) {
            return;
        }
        SystemTray.getSystemTray().remove(trayIcon);
        trayIcon = null;
    }

    private PopupMenu buildMenu() {
        PopupMenu menu = new PopupMenu();

        for (MenuEntry entrada : buildMenuModel()) {
            if (entrada.isSeparator()) {
                menu.addSeparator();
                continue;
            }
            MenuItem item = new MenuItem(entrada.label());
            Runnable acao = entrada.action();
            if (acao != null) {
                item.addActionListener(e -> runAction(acao));
            }
            menu.add(item);
        }

        return menu;
    }

    private void runAction(Runnable action) {
        if (action != null) {
            action.run();
        }
    }

    private Image loadIcon() {
        Image loaded = IconLoader.load(settings.getIconPath());
        if (loaded != null) {
            return loaded;
        }
        log.warn("Icone '{}' nao encontrado. Usando icone padrao.", settings.getIconPath());
        return IconLoader.createFallbackIcon(Message.TRAY_TITLE.getValue(), DEFAULT_ICON_SIZE);
    }
}

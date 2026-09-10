package app.service;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.utils.Message;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

/**
 * Popup nativo do Windows via TaskDialog (comctl32 v6).
 *
 * <p>Preferido ao balao do TrayIcon porque este e descartado em silencio pelo
 * Shell quando as notificacoes do Windows estao desabilitadas (ToastEnabled=0),
 * sem lancar excecao. O TaskDialog nao depende dessa configuracao e permite
 * icone proprio e botoes personalizados.</p>
 *
 * <p><b>Layout nativo:</b> no SDK a TASKDIALOGCONFIG e a TASKDIALOG_BUTTON estao
 * dentro de {@code pshpack1.h}, ou seja, sao empacotadas (160 bytes em x64). Com
 * alinhamento natural do JNA daria 176 e a API devolveria E_INVALIDARG, por isso
 * a struct usa ALIGN_NONE e o array de botoes e escrito com passo de 12 bytes.</p>
 */
public final class TaskDialogService {

    private static final Logger log = LoggerFactory.getLogger(TaskDialogService.class);

    private static final int TDF_ENABLE_HYPERLINKS = 0x0001;
    private static final int TDF_USE_HICON_MAIN = 0x0002;

    // TDF_ALLOW_DIALOG_CANCELLATION (0x0008) e omitida de proposito: e ela que
    // habilita o X, o Alt+F4 e o Esc. Sem ela, e sem TDCBF_CANCEL_BUTTON, a
    // unica forma de responder e escolher um dos botoes.

    /** TASKDIALOG_BUTTON em layout empacotado x64. */
    private static final int BUTTON_ENTRY_SIZE = 12;

    private static final int ID_DOWNLOAD = 100;
    private static final int ID_POSTPONE = 101;

    static final int BUTTON_COUNT = 2;

    static final int OFFSET_BUTTON_ID = 0;
    static final int OFFSET_BUTTON_TEXT = 4;

    /**
     * Se o JNA mudar o alinhamento este numero deixa de bater e o popup retorna
     * E_INVALIDARG - por isso e verificado por teste.
     */
    static final int EXPECTED_CONFIG_SIZE_X64 = 160;

    private static final String LABEL_DOWNLOAD = Message.BUTTON_DOWNLOAD.getValue();
    private static final String LABEL_POSTPONE = Message.BUTTON_POSTPONE.getValue();

    public enum Outcome {
        /** Popup exibido e o usuario escolheu "Baixar agora". */
        SHOWN_ACCEPTED,
        /** Popup exibido e o usuario escolheu "Adiar" (ou fechou). */
        SHOWN_DECLINED,
        /** O popup nao pode ser exibido (API indisponivel ou erro). */
        NOT_SHOWN
    }

    private TaskDialogService() {
    }

    public interface ComCtl32 extends StdCallLibrary {
        ComCtl32 INSTANCE = Native.load("comctl32", ComCtl32.class, W32APIOptions.UNICODE_OPTIONS);

        int TaskDialogIndirect(TASKDIALOGCONFIG pTaskConfig,
                               IntByReference pnButton,
                               IntByReference pnRadioButton,
                               IntByReference pfVerificationFlagChecked);
    }

    /**
     * Layout empacotado da TASKDIALOGCONFIG. As duas unioes de icone
     * (hMainIcon/pszMainIcon e hFooterIcon/pszFooterIcon) sao representadas por
     * um unico ponteiro, que ocupa o mesmo espaco de ambas.
     */
    @Structure.FieldOrder({"cbSize", "hwndParent", "hInstance", "dwFlags", "dwCommonButtons",
            "pszWindowTitle", "mainIcon", "pszMainInstruction", "pszContent",
            "cButtons", "pButtons", "nDefaultButton", "cRadioButtons", "pRadioButtons",
            "nDefaultRadioButton", "pszVerificationText", "pszExpandedInformation",
            "pszExpandedControlText", "pszCollapsedControlText", "footerIcon",
            "pszFooter", "pfCallback", "lpCallbackData", "cxWidth"})
    public static final class TASKDIALOGCONFIG extends Structure {
        public int cbSize;
        public Pointer hwndParent;
        public Pointer hInstance;
        public int dwFlags;
        public int dwCommonButtons;
        public WString pszWindowTitle;
        public Pointer mainIcon;
        public WString pszMainInstruction;
        public WString pszContent;
        public int cButtons;
        public Pointer pButtons;
        public int nDefaultButton;
        public int cRadioButtons;
        public Pointer pRadioButtons;
        public int nDefaultRadioButton;
        public WString pszVerificationText;
        public WString pszExpandedInformation;
        public WString pszExpandedControlText;
        public WString pszCollapsedControlText;
        public Pointer footerIcon;
        public WString pszFooter;
        public Pointer pfCallback;
        public Pointer lpCallbackData;
        public int cxWidth;

        public TASKDIALOGCONFIG() {
            super();
            // Layout empacotado, exigido pela API nativa.
            setAlignType(ALIGN_NONE);
        }
    }

    /**
     * Exibe o popup de forma bloqueante com os botoes "Baixar agora" e "Adiar".
     * Nao chamar na Event Dispatch Thread.
     */
    public static Outcome showUpdateDialog(String windowTitle, String mainInstruction, String content,
                                           String expandedInformation, String iconPath) {
        Pointer hIcon = null;
        // Mantem os buffers nativos vivos durante toda a chamada.
        List<Object> keepAlive = new ArrayList<>();

        try {
            hIcon = WinApiService.loadIcon(iconPath);

            Memory buttons = buildButtonArray(keepAlive);

            TASKDIALOGCONFIG config = new TASKDIALOGCONFIG();
            config.pszWindowTitle = new WString(windowTitle);
            config.pszMainInstruction = new WString(mainInstruction);
            config.pszContent = new WString(content);
            config.pszExpandedInformation = new WString(expandedInformation);
            config.pszExpandedControlText = new WString("Detalhes");
            config.pszCollapsedControlText = new WString("Ocultar detalhes");
            config.cButtons = 2;
            config.pButtons = buttons;
            config.nDefaultButton = ID_DOWNLOAD;
            // Sem TDF_ALLOW_DIALOG_CANCELLATION: o X da janela fica desabilitado.
            config.dwFlags = TDF_ENABLE_HYPERLINKS;

            if (hIcon != null) {
                config.mainIcon = hIcon;
                config.dwFlags |= TDF_USE_HICON_MAIN;
            }

            config.cbSize = config.size();
            config.write();

            IntByReference pressedButton = new IntByReference(0);
            int hResult = ComCtl32.INSTANCE.TaskDialogIndirect(config, pressedButton, null, null);

            if (hResult != 0) {
                log.warn("TaskDialogIndirect falhou (hr=0x{}).", Integer.toHexString(hResult));
                return Outcome.NOT_SHOWN;
            }

            int chosen = pressedButton.getValue();
            log.info("Popup nativo exibido. Botao escolhido: {} ({})",
                    chosen, chosen == ID_DOWNLOAD ? LABEL_DOWNLOAD : LABEL_POSTPONE);
            return chosen == ID_DOWNLOAD ? Outcome.SHOWN_ACCEPTED : Outcome.SHOWN_DECLINED;
        } catch (Throwable t) {
            log.warn("Nao foi possivel exibir o popup nativo: {}", t.getMessage());
            return Outcome.NOT_SHOWN;
        } finally {
            WinApiService.destroyIcon(hIcon);
            // Impede o JIT de descartar os buffers antes da chamada nativa terminar.
            Reference.reachabilityFence(keepAlive);
        }
    }

    /**
     * Monta o array de TASKDIALOG_BUTTON: id no offset 0, ponteiro do texto no
     * offset 4, passo de 12 bytes. Visivel no pacote para os testes validarem os
     * offsets sem exibir o dialogo, que e modal.
     */
    static Memory buildButtonArray(List<Object> keepAlive) {
        Memory buttons = new Memory((long) BUTTON_COUNT * BUTTON_ENTRY_SIZE);
        buttons.clear();

        Memory downloadText = newWideString(LABEL_DOWNLOAD, keepAlive);
        Memory postponeText = newWideString(LABEL_POSTPONE, keepAlive);

        buttons.setInt(OFFSET_BUTTON_ID, ID_DOWNLOAD);
        buttons.setPointer(OFFSET_BUTTON_TEXT, downloadText);
        buttons.setInt(BUTTON_ENTRY_SIZE + OFFSET_BUTTON_ID, ID_POSTPONE);
        buttons.setPointer(BUTTON_ENTRY_SIZE + OFFSET_BUTTON_TEXT, postponeText);

        keepAlive.add(buttons);
        return buttons;
    }

    static int downloadButtonId() {
        return ID_DOWNLOAD;
    }

    static int postponeButtonId() {
        return ID_POSTPONE;
    }

    static int buttonEntrySize() {
        return BUTTON_ENTRY_SIZE;
    }

    private static Memory newWideString(String value, List<Object> keepAlive) {
        Memory memory = new Memory(((long) value.length() + 1) * 2);
        memory.clear();
        memory.setWideString(0, value);
        keepAlive.add(memory);
        return memory;
    }
}

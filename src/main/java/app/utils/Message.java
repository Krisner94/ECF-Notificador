package app.utils;

public enum Message {
    TRAY_TITLE("ECF Notificador"),
    MENU_CHECK_NOW("Verificar agora"),
    MENU_SETTINGS("Configurações"),
    MENU_EXIT("Sair"),
    NOTIFICATION_UPDATE_TITLE("Atualização disponível"),
    BUTTON_DOWNLOAD("Baixar agora"),
    BUTTON_POSTPONE("Adiar"),
    ERROR_CHECK_FAILED("Erro ao verificar atualizações.");

    private final String value;

    Message(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

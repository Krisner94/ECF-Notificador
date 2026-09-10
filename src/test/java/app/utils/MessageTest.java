package app.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Garante que as mensagens exibidas ao usuario estao sempre preenchidas. */
class MessageTest {

    @ParameterizedTest
    @EnumSource(Message.class)
    @DisplayName("Toda mensagem tem texto nao vazio")
    void todaMensagemTemTexto(Message mensagem) {
        assertNotNull(mensagem.getValue(), "Mensagem " + mensagem + " esta nula");
        assertFalse(mensagem.getValue().isBlank(), "Mensagem " + mensagem + " esta vazia");
    }

    @ParameterizedTest
    @EnumSource(Message.class)
    @DisplayName("Nenhuma mensagem tem espacos sobrando nas pontas")
    void nenhumaMensagemTemEspacosSobrando(Message mensagem) {
        assertEquals(mensagem.getValue().trim(), mensagem.getValue(),
                "A mensagem " + mensagem + " tem espacos no inicio ou no fim.");
    }

    @ParameterizedTest
    @EnumSource(Message.class)
    @DisplayName("Nenhuma mensagem faz pergunta ao usuario")
    void nenhumaMensagemFazPergunta(Message mensagem) {
        assertFalse(mensagem.getValue().contains("?"),
                "A mensagem " + mensagem + " nao deve conter pergunta.");
    }

    @ParameterizedTest
    @EnumSource(Message.class)
    @DisplayName("Nenhuma mensagem quebra linha (o popup nativo formata o texto)")
    void nenhumaMensagemQuebraLinha(Message mensagem) {
        assertFalse(mensagem.getValue().contains("\n"),
                "A mensagem " + mensagem + " nao deve conter quebra de linha.");
        assertFalse(mensagem.getValue().contains("\r"),
                "A mensagem " + mensagem + " nao deve conter retorno de carro.");
    }

    @ParameterizedTest
    @EnumSource(Message.class)
    @DisplayName("Nenhuma mensagem tem espacos duplicados")
    void nenhumaMensagemTemEspacosDuplicados(Message mensagem) {
        assertFalse(mensagem.getValue().contains("  "),
                "A mensagem " + mensagem + " tem espacos duplicados.");
    }

    @Test
    @DisplayName("Os botoes do popup usam os textos definidos pelo produto")
    void botoesDoPopupTemTextosEsperados() {
        assertEquals("Baixar agora", Message.BUTTON_DOWNLOAD.getValue());
        assertEquals("Adiar", Message.BUTTON_POSTPONE.getValue());
    }

    @Test
    @DisplayName("Os itens do menu usam os textos definidos pelo produto")
    void itensDoMenuTemTextosEsperados() {
        assertEquals("Verificar agora", Message.MENU_CHECK_NOW.getValue());
        assertEquals("Configura\u00e7\u00f5es", Message.MENU_SETTINGS.getValue());
        assertEquals("Sair", Message.MENU_EXIT.getValue());
    }

    @Test
    @DisplayName("O titulo da notificacao descreve o aviso, sem perguntar")
    void tituloDaNotificacaoEhInformativo() {
        assertEquals("Atualiza\u00e7\u00e3o dispon\u00edvel",
                Message.NOTIFICATION_UPDATE_TITLE.getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MENU_CHECK_NOW", "MENU_SETTINGS", "MENU_EXIT",
            "BUTTON_DOWNLOAD", "BUTTON_POSTPONE", "TRAY_TITLE",
            "NOTIFICATION_UPDATE_TITLE", "ERROR_CHECK_FAILED"})
    @DisplayName("Todas as constantes esperadas existem no enum")
    void constantesEsperadasExistem(String nome) {
        // Falha se alguem remover uma mensagem usada pela interface.
        assertTrue(java.util.Arrays.stream(Message.values())
                        .anyMatch(m -> m.name().equals(nome)),
                "A mensagem " + nome + " deveria existir em Message.");
    }
}

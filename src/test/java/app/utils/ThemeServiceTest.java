package app.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.UIManager;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes da instalacao do tema.
 *
 * <p>Rodam em modo headless (configurado no {@code pom.xml} e na execucao
 * local): o objetivo e garantir que a instalacao nunca derruba a aplicacao e
 * que a fonte base e aplicada, nao validar cores que dependem do Windows.</p>
 */
class ThemeServiceTest {

    @Test
    @DisplayName("A instalacao do tema nao lanca excecao mesmo sem ambiente grafico")
    void instalacaoNaoLancaExcecao() {
        // Nao deve propagar erro: o App chama isso antes de qualquer janela.
        ThemeService.install();
    }

    @Test
    @DisplayName("A instalacao e idempotente")
    void instalacaoEhIdempotente() {
        ThemeService.install();
        ThemeService.install();
    }

    @Test
    @DisplayName("A fonte base fica definida apos a instalacao")
    void fonteBaseDefinidaAposInstalacao() {
        ThemeService.install();

        // Com FlatLaf disponivel no classpath, defaultFont e definido;
        // sem ele, o Look and Feel padrao tambem registra defaultFont.
        assertNotNull(UIManager.get("defaultFont"),
                "A fonte base deveria estar registrada no UIManager.");
    }

    @Test
    @DisplayName("Um Look and Feel permanece instalado apos a chamada")
    void lookAndFeelInstalado() {
        ThemeService.install();

        assertNotNull(UIManager.getLookAndFeel(),
                "Sempre deve haver um Look and Feel ativo apos a instalacao.");
        assertTrue(UIManager.getLookAndFeel().getName() != null
                        && !UIManager.getLookAndFeel().getName().isBlank(),
                "O Look and Feel instalado deveria ter nome.");
    }
}

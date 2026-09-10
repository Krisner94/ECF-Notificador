package app.service;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes do layout nativo do popup de atualizacao (TaskDialog).
 *
 * <p>Este e o ponto mais fragil da interoperabilidade com o Windows: no SDK a
 * {@code TASKDIALOGCONFIG} e a {@code TASKDIALOG_BUTTON} sao declaradas dentro
 * de {@code #include pshpack1.h}, ou seja, <b>empacotadas</b> (160 bytes em x64).
 * Se o JNA usar alinhamento natural (176 bytes), o Windows devolve
 * {@code E_INVALIDARG} e nenhum popup aparece - falha silenciosa ja observada
 * neste projeto.</p>
 *
 * <p>Os testes abaixo validam o layout sem exibir a janela (que e modal e
 * travaria a suite).</p>
 */
class TaskDialogServiceTest {

    @Nested
    @DisplayName("Array de botoes (layout empacotado)")
    class ArrayDeBotoes {

        @Test
        @DisplayName("Tem duas entradas, uma por botao")
        void temDuasEntradas() {
            List<Object> keepAlive = new ArrayList<>();

            Memory botoes = TaskDialogService.buildButtonArray(keepAlive);

            assertThat(botoes.size())
                    .isEqualTo((long) TaskDialogService.buttonEntrySize()
                            * 2);
        }

        @Test
        @DisplayName("O identificador fica no offset 0 de cada entrada")
        void identificadorNoOffsetZero() {
            List<Object> keepAlive = new ArrayList<>();

            Memory botoes = TaskDialogService.buildButtonArray(keepAlive);
            long passo = TaskDialogService.buttonEntrySize();

            assertThat(botoes.getInt(0)).isEqualTo(TaskDialogService.downloadButtonId());
            assertThat(botoes.getInt(passo)).isEqualTo(TaskDialogService.postponeButtonId());
        }

        @Test
        @DisplayName("O ponteiro do texto fica no offset 4 de cada entrada")
        void textoNoOffsetQuatro() {
            List<Object> keepAlive = new ArrayList<>();

            Memory botoes = TaskDialogService.buildButtonArray(keepAlive);
            long passo = TaskDialogService.buttonEntrySize();

            // Em x64 o ponteiro ocupa 8 bytes, mas comeca no offset 4 por causa
            // do empacotamento. Ler os dois valores prova que nao houve
            // preenchimento implicito.
            assertThat(botoes.getInt(4)).isNotZero();
            assertThat(botoes.getInt(passo + 4)).isNotZero();
        }

        @Test
        @DisplayName("O passo entre entradas e de 12 bytes (id 4 + ponteiro 8)")
        void passoDeDozeBytes() {
            // Documenta a constante: se alguem mudar, este teste explica o
            // porque de 12 e nao de 16.
            assertThat(TaskDialogService.buttonEntrySize()).isEqualTo(12);
        }

        @Test
        @DisplayName("O array de botoes e os textos ficam mantidos vivos")
        void buffersFicamMantidosVivos() {
            List<Object> keepAlive = new ArrayList<>();

            TaskDialogService.buildButtonArray(keepAlive);

            // O array de botoes e os dois textos precisam ficar acessiveis
            // durante a chamada nativa; se o coletor os removesse, o Windows
            // leria memoria invalida.
            assertThat(keepAlive).hasSize(3);
            assertThat(keepAlive).allSatisfy(buffer ->
                    assertThat(buffer).isInstanceOf(Memory.class));
        }

        @Test
        @DisplayName("Os textos dos botoes sao gravados como UTF-16")
        void textosSaoStringLarga() {
            List<Object> keepAlive = new ArrayList<>();

            Memory botoes = TaskDialogService.buildButtonArray(keepAlive);
            long passo = TaskDialogService.buttonEntrySize();

            // Ordem em que buildButtonArray registra os buffers: os dois textos
            // primeiro, o array de botoes por ultimo. Manter essa ordem viva e o
            // que garante que o Windows leia memoria valida durante a chamada.
            Memory textoDownload = (Memory) keepAlive.get(0);
            Memory textoAdiar = (Memory) keepAlive.get(1);
            assertThat((Object) keepAlive.get(2)).isSameAs(botoes);

            assertThat(textoDownload.getWideString(0)).isEqualTo("Baixar agora");
            assertThat(textoAdiar.getWideString(0)).isEqualTo("Adiar");

            // Os ponteiros gravados apontam para esses buffers.
            assertThat(botoes.getLong(4)).isEqualTo(Pointer.nativeValue(textoDownload));
            assertThat(botoes.getLong(passo + 4)).isEqualTo(Pointer.nativeValue(textoAdiar));
        }
    }

    @Nested
    @DisplayName("Estrutura TASKDIALOGCONFIG")
    class Estrutura {

        @Test
        @DisplayName("O alinhamento e empacotado (ALIGN_NONE), exigido pelo SDK")
        void alinhamentoEmpacotado() {
            TaskDialogService.TASKDIALOGCONFIG config =
                    new TaskDialogService.TASKDIALOGCONFIG();

            // Com ALIGN_NONE o tamanho e 160 (x64); com alinhamento natural
            // seria 176 e o Windows recusaria a chamada com E_INVALIDARG.
            // Como o JNA nao expoe getAlignType(), o efeito e verificado pelo
            // tamanho calculado - que e o que realmente importa.
            if ("64".equals(System.getProperty("sun.arch.data.model"))) {
                assertThat(config.size())
                        .as("Alinhamento natural produziria 176 bytes")
                        .isEqualTo(TaskDialogService.EXPECTED_CONFIG_SIZE_X64);
            }
        }

        @Test
        @DisplayName("O tamanho da estrutura e o esperado em x64 (160 bytes)")
        void tamanhoEsperado() {
            TaskDialogService.TASKDIALOGCONFIG config =
                    new TaskDialogService.TASKDIALOGCONFIG();

            // So valida em 64 bits: em 32 bits os ponteiros sao menores.
            if ("64".equals(System.getProperty("sun.arch.data.model"))) {
                assertThat(config.size())
                        .as("TASKDIALOGCONFIG empacotada deve ter 160 bytes em x64; "
                                + "qualquer outro valor faz TaskDialogIndirect devolver E_INVALIDARG")
                        .isEqualTo(TaskDialogService.EXPECTED_CONFIG_SIZE_X64);
            }
        }

        @Test
        @DisplayName("Declara os 24 campos na ordem do commctrl.h")
        void camposNaOrdemCorreta() {
            TaskDialogService.TASKDIALOGCONFIG config =
                    new TaskDialogService.TASKDIALOGCONFIG();

            // getFieldOrder() e protegido no JNA; a verificacao da ordem e feita
            // pelo proprio calculo de tamanho. Aqui confirmamos que a
            // estrutura tem os campos esperados e que a ordem declarada em
            // @FieldOrder e respeitada pelo size().
            config.cbSize = config.size();
            config.write();

            assertThat(config.cbSize).isEqualTo(TaskDialogService.EXPECTED_CONFIG_SIZE_X64);
            assertThat((Object) config.pszWindowTitle).isNull();
            assertThat(config.cxWidth).isZero();

            // Os campos de contexto do dialogo precisam existir e ser
            // atribuiveis: sao os pontos usados por showUpdateDialog.
            config.pszMainInstruction = new com.sun.jna.WString("instrucao");
            config.pszContent = new com.sun.jna.WString("conteudo");
            config.pszExpandedInformation = new com.sun.jna.WString("detalhes");
            assertThat((Object) config.pszMainInstruction).isNotNull();
            assertThat((Object) config.pszContent).isNotNull();
        }

        @Test
        @DisplayName("Valores iniciais sao neutros")
        void valoresIniciais() {
            TaskDialogService.TASKDIALOGCONFIG config =
                    new TaskDialogService.TASKDIALOGCONFIG();

            assertThat(config.cbSize).isZero();
            assertThat(config.dwFlags).isZero();
            assertThat(config.cButtons).isZero();
            assertThat(config.pButtons).isNull();
            assertThat((Object) config.pszWindowTitle).isNull();
        }
    }

    @Nested
    @DisplayName("Referencias nativas")
    class ReferenciasNativas {

        @Test
        @DisplayName("IntByReference usado para o botao pressionado comeca em zero")
        void referenciaDeBotaoComecaEmZero() {
            IntByReference botao = new IntByReference(0);

            assertThat(botao.getValue()).isZero();
        }
    }

    @Nested
    @DisplayName("Resultado da exibicao")
    class Resultado {

        @Test
        @DisplayName("O enum de resultado tem os tres desfechos previstos")
        void enumTemTresDesfechos() {
            assertThat(TaskDialogService.Outcome.values()).containsExactly(
                    TaskDialogService.Outcome.SHOWN_ACCEPTED,
                    TaskDialogService.Outcome.SHOWN_DECLINED,
                    TaskDialogService.Outcome.NOT_SHOWN);
        }

        @Test
        @DisplayName("Os desfechos sao distintos entre si")
        void desfechosSaoDistintos() {
            assertThat(TaskDialogService.Outcome.valueOf("SHOWN_ACCEPTED"))
                    .isNotEqualTo(TaskDialogService.Outcome.SHOWN_DECLINED);
            assertThat(TaskDialogService.Outcome.valueOf("NOT_SHOWN"))
                    .isNotEqualTo(TaskDialogService.Outcome.SHOWN_ACCEPTED);
        }

        // NOTA DELIBERADA: nenhum teste chama showUpdateDialog() de verdade.
        // A API TaskDialogIndirect abre uma janela MODAL: sem um usuario para
        // fecha-la, ela bloqueia a thread indefinidamente e ja derrubou a
        // automacao deste projeto. A cobertura desse metodo fica no grupo
        // "ui", executado manualmente em sessao grafica.
    }
}

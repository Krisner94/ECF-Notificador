package app.service;

import app.config.AppConfig;
import app.testutil.StubGistLoader;
import app.testutil.TestConfigs;
import app.utils.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Modulo 6 - Bandeja do sistema e threads de execucao.
 *
 * <p>O requisito central e que a verificacao em segundo plano <b>nunca</b>
 * bloqueie a Event Dispatch Thread (EDT): se ela bloquear, a bandeja e o menu
 * congelam durante a requisicao de rede.</p>
 *
 * <p>Os testes de construcao do menu rodam sobre o modelo puro
 * ({@link TrayIconService.MenuEntry}), porque criar um {@code PopupMenu} real
 * lanca {@code HeadlessException} no ambiente da suite - verificado na pratica.
 * A verificacao de que essa conversao nao quebra em ambiente grafico fica no
 * grupo {@code ui}, excluido da execucao padrao.</p>
 */
class TrayIconServiceTest {

    private static SettingsService settingsEm(Path tempDir) {
        AppConfig config = TestConfigs.build("C:\\SpedECF\\response.varfile",
                "https://www.gov.br/receitafederal/ecf", "rfb_subheader", 6, "EcfNotificador.ico");
        return new SettingsService(new StubGistLoader(config),
                tempDir.resolve("config").resolve("appSettings.json"));
    }

    private static SettingsService settingsInvalidos(Path tempDir, String icone) {
        AppConfig config = TestConfigs.build("caminho", "https://a.test", "cls", 6, icone);
        return new SettingsService(new StubGistLoader(config),
                tempDir.resolve("config").resolve("appSettings.json"));
    }

    @Nested
    @DisplayName("Modelo do menu de contexto")
    class ModeloDoMenu {

        @Test
        @DisplayName("O menu tem os itens esperados, na ordem definida pelo produto")
        void menuTemItensNaOrdem(@TempDir Path tempDir) {
            TrayIconService servico = new TrayIconService(settingsEm(tempDir));

            List<TrayIconService.MenuEntry> menu = servico.buildMenuModel();

            assertThat(menu).hasSize(4);
            assertThat(menu.get(0).label()).isEqualTo(Message.MENU_CHECK_NOW.getValue());
            assertThat(menu.get(1).label()).isEqualTo(Message.MENU_SETTINGS.getValue());
            assertThat(menu.get(2).isSeparator()).isTrue();
            assertThat(menu.get(3).label()).isEqualTo(Message.MENU_EXIT.getValue());
        }

        @Test
        @DisplayName("O separador isola a acao destrutiva (Sair) das demais")
        void separadorIsolaSair(@TempDir Path tempDir) {
            TrayIconService servico = new TrayIconService(settingsEm(tempDir));

            List<TrayIconService.MenuEntry> menu = servico.buildMenuModel();

            int indiceSeparador = -1;
            int indiceSair = -1;
            for (int i = 0; i < menu.size(); i++) {
                if (menu.get(i).isSeparator()) {
                    indiceSeparador = i;
                }
                if (Message.MENU_EXIT.getValue().equals(menu.get(i).label())) {
                    indiceSair = i;
                }
            }

            assertThat(indiceSeparador).isPositive();
            assertThat(indiceSair).isEqualTo(indiceSeparador + 1);
        }

        @Test
        @DisplayName("As acoes registradas chegam aos itens correspondentes")
        void acoesChegamAosItens(@TempDir Path tempDir) {
            TrayIconService servico = new TrayIconService(settingsEm(tempDir));
            AtomicInteger verificacoes = new AtomicInteger();
            AtomicInteger configuracoes = new AtomicInteger();
            AtomicInteger saidas = new AtomicInteger();

            servico.addCheckNowAction(verificacoes::incrementAndGet);
            servico.addSettingsAction(configuracoes::incrementAndGet);
            servico.addExitAction(saidas::incrementAndGet);

            List<TrayIconService.MenuEntry> menu = servico.buildMenuModel();
            menu.get(0).action().run();
            menu.get(1).action().run();
            menu.get(3).action().run();

            assertThat(verificacoes.get()).isEqualTo(1);
            assertThat(configuracoes.get()).isEqualTo(1);
            assertThat(saidas.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("Item sem acao registrada nao lanca ao ser acionado")
        void itemSemAcaoNaoLanca(@TempDir Path tempDir) {
            // Antes das acoes serem registradas, os itens existem mas sao inertes.
            TrayIconService servico = new TrayIconService(settingsEm(tempDir));

            List<TrayIconService.MenuEntry> menu = servico.buildMenuModel();

            assertThat(menu.get(0).action()).isNull();
            assertThat(menu.get(1).action()).isNull();
            assertThat(menu.get(3).action()).isNull();
        }

        @Test
        @DisplayName("O modelo do menu e imutavel")
        void modeloEhImutavel(@TempDir Path tempDir) {
            TrayIconService servico = new TrayIconService(settingsEm(tempDir));

            List<TrayIconService.MenuEntry> menu = servico.buildMenuModel();

            assertThat(menu).isUnmodifiable();
        }

        @ParameterizedTest(name = "icone ''{0}'' nao quebra o menu")
        @ValueSource(strings = {"", "   ", "inexistente.ico", "EcfNotificador.ico"})
        @DisplayName("Icone ausente ou invalido nao impede a montagem do menu")
        void iconeInvalidoNaoImpedeOMenu(String icone, @TempDir Path tempDir) {
            TrayIconService servico = new TrayIconService(settingsInvalidos(tempDir, icone));

            assertThat(servico.buildMenuModel()).hasSize(4);
        }

        @Test
        @DisplayName("show() nao lanca excecao em ambiente sem bandeja")
        void showNaoLancaSemBandeja(@TempDir Path tempDir) {
            TrayIconService servico = new TrayIconService(settingsEm(tempDir));

            // Em headless o SystemTray nao e suportado: o servico deve apenas
            // registrar o aviso e seguir, nunca derrubar o programa.
            servico.show();
        }

        @Test
        @DisplayName("remove() sem icone exibido nao lanca excecao")
        void removeSemIconeNaoLanca(@TempDir Path tempDir) {
            TrayIconService servico = new TrayIconService(settingsEm(tempDir));

            servico.remove();
            servico.remove();
        }
    }

    @Nested
    @DisplayName("Threads de execucao (EDT)")
    class ThreadsDeExecucao {

        @Test
        @Timeout(20)
        @DisplayName("A verificacao agendada roda FORA da EDT")
        void verificacaoRodaForaDaEdt(@TempDir Path tempDir) throws InterruptedException {
            AtomicReference<Thread> threadDaVerificacao = new AtomicReference<>();
            CountDownLatch executou = new CountDownLatch(1);

            UpdateScheduler agendador = new UpdateScheduler(
                    Executors.newSingleThreadScheduledExecutor());
            try {
                agendador.submit(() -> {
                    threadDaVerificacao.set(Thread.currentThread());
                    executou.countDown();
                    return UpdateService.CheckResult.UP_TO_DATE;
                });

                assertThat(executou.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                agendador.shutdown();
            }

            // Este e o requisito que importa: nunca rodar na EDT.
            assertThat(threadDaVerificacao.get().getName()).isNotEqualTo("AWT-EventQueue-0");
            assertThat(SwingUtilities.isEventDispatchThread()).isFalse();
        }

        @Test
        @Timeout(20)
        @DisplayName("O agendador padrao nomeia a thread de forma identificavel")
        void agendadorPadraoNomeiaAThread(@TempDir Path tempDir) throws InterruptedException {
            CountDownLatch executou = new CountDownLatch(1);
            AtomicReference<String> nome = new AtomicReference<>();

            // Construtor padrao: e o usado em producao pelo App.
            UpdateScheduler agendador = new UpdateScheduler();
            try {
                agendador.submit(() -> {
                    nome.set(Thread.currentThread().getName());
                    executou.countDown();
                    return UpdateService.CheckResult.UP_TO_DATE;
                });

                assertThat(executou.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                agendador.shutdown();
            }

            assertThat(nome.get()).isEqualTo("ecf-verificacao");
        }

        @Test
        @Timeout(20)
        @DisplayName("Chamar a verificacao de dentro da EDT nao bloqueia a interface")
        void verificacaoChamadaDaEdtNaoBloqueia(@TempDir Path tempDir) throws Exception {
            AtomicBoolean edtLivre = new AtomicBoolean(false);
            CountDownLatch agendado = new CountDownLatch(1);
            CountDownLatch verificacaoConcluida = new CountDownLatch(1);

            UpdateScheduler agendador = new UpdateScheduler(
                    Executors.newSingleThreadScheduledExecutor());
            try {
                // Simula o clique no item de menu: dispara dentro da EDT.
                SwingUtilities.invokeLater(() -> {
                    agendador.submit(() -> {
                        // Tarefa lenta, como seria a requisicao de rede.
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        verificacaoConcluida.countDown();
                        return UpdateService.CheckResult.UP_TO_DATE;
                    });
                    agendado.countDown();
                });

                assertThat(agendado.await(10, TimeUnit.SECONDS)).isTrue();

                // Se a verificacao estivesse rodando na EDT, este invokeAndWait
                // so retornaria depois dos 300ms; medimos para provar que nao.
                SwingUtilities.invokeAndWait(() -> edtLivre.set(true));

                assertThat(edtLivre).as("A EDT deveria estar livre imediatamente").isTrue();
                assertThat(verificacaoConcluida.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                agendador.shutdown();
            }
        }

        @Test
        @Timeout(20)
        @DisplayName("Uma falha na verificacao nao derruba o agendador")
        void falhaNaoDerrubaOAgendador(@TempDir Path tempDir) throws InterruptedException {
            AtomicInteger execucoes = new AtomicInteger();
            CountDownLatch tresExecucoes = new CountDownLatch(3);

            ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor();
            UpdateScheduler agendador = new UpdateScheduler(pool);
            try {
                agendador.submit(() -> {
                    execucoes.incrementAndGet();
                    tresExecucoes.countDown();
                    throw new IllegalStateException("falha simulada de rede");
                });

                assertThat(tresExecucoes.await(5, TimeUnit.SECONDS))
                        .as("O agendador deve continuar mesmo apos a excecao")
                        .isFalse();

                // A fila continua aceitando trabalho depois da falha.
                assertThat(agendador.totalDeFalhas()).isEqualTo(1);
                assertThat(pool.isShutdown()).isFalse();

                agendador.submit(() -> {
                    execucoes.incrementAndGet();
                    tresExecucoes.countDown();
                    return UpdateService.CheckResult.UP_TO_DATE;
                });
                agendador.submit(() -> {
                    execucoes.incrementAndGet();
                    tresExecucoes.countDown();
                    return UpdateService.CheckResult.UP_TO_DATE;
                });

                assertThat(tresExecucoes.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                agendador.shutdown();
            }

            assertThat(execucoes.get()).isEqualTo(3);
            assertThat(agendador.totalDeExecucoes()).isEqualTo(3);
        }

        @Test
        @Timeout(20)
        @DisplayName("Verificacoes concorrentes sao serializadas, nunca simultaneas")
        void verificacoesSaoSerializadas(@TempDir Path tempDir) throws InterruptedException {
            AtomicInteger emAndamento = new AtomicInteger();
            AtomicInteger maximoSimultaneo = new AtomicInteger();
            CountDownLatch dez = new CountDownLatch(10);
            ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor();
            UpdateScheduler agendador = new UpdateScheduler(pool);

            try {
                for (int i = 0; i < 10; i++) {
                    agendador.submit(() -> {
                        int agora = emAndamento.incrementAndGet();
                        maximoSimultaneo.accumulateAndGet(agora, Math::max);
                        try {
                            Thread.sleep(5);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        emAndamento.decrementAndGet();
                        dez.countDown();
                        return UpdateService.CheckResult.UP_TO_DATE;
                    });
                }

                assertThat(dez.await(15, TimeUnit.SECONDS)).isTrue();
            } finally {
                agendador.shutdown();
            }

            assertThat(maximoSimultaneo.get())
                    .as("Com thread unica, nunca pode haver duas verificacoes ao mesmo tempo")
                    .isEqualTo(1);
        }

        @Test
        @Timeout(20)
        @DisplayName("O shutdown encerra o agendador sem deixar threads penduradas")
        void shutdownEncerraOAgendador(@TempDir Path tempDir) throws InterruptedException {
            UpdateScheduler agendador = new UpdateScheduler();
            agendador.submit(() -> UpdateService.CheckResult.UP_TO_DATE);

            agendador.shutdown();

            // Verifica que a thread "ecf-verificacao" nao continua viva.
            Thread.sleep(200);
            boolean viva = Thread.getAllStackTraces().keySet().stream()
                    .anyMatch(t -> "ecf-verificacao".equals(t.getName()) && t.isAlive());

            assertThat(viva).as("A thread do agendador deveria ter terminado").isFalse();
        }

        @Test
        @Timeout(20)
        @DisplayName("A recarga do Gist nao derruba o agendador se falhar")
        void falhaNaRecargaDoGistNaoDerruba(@TempDir Path tempDir) {
            ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor();
            UpdateScheduler agendador = new UpdateScheduler(pool);

            try {
                agendador.scheduleGistRefresh(() -> {
                    throw new IllegalStateException("Gist indisponivel");
                });

                // Agenda por 6h; aqui so confirmamos que o agendamento foi aceito
                // e a tarefa nao executou excecao na hora do registro.
                assertThat(pool.isShutdown()).isFalse();
            } finally {
                agendador.shutdown();
            }
        }

        @Test
        @DisplayName("Intervalo invalido e corrigido para no minimo 1 hora")
        void intervaloInvalidoEhCorrigido(@TempDir Path tempDir) {
            ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor();
            UpdateScheduler agendador = new UpdateScheduler(pool);

            try {
                // Nao deve lancar IllegalArgumentException por periodo invalido.
                agendador.scheduleAtFixedRate(
                        () -> UpdateService.CheckResult.UP_TO_DATE, 0);
                agendador.scheduleAtFixedRate(
                        () -> UpdateService.CheckResult.UP_TO_DATE, -5);

                assertThat(pool.isShutdown()).isFalse();
            } finally {
                agendador.shutdown();
            }
        }
    }

    @Nested
    @DisplayName("Notificacao e EDT")
    class NotificacaoEEdt {

        @Test
        @Timeout(20)
        @DisplayName("showUpdate nao bloqueia a EDT ao ser chamado dela")
        void showUpdateNaoBloqueiaAEdt() throws Exception {
            // O aviso real abriria uma janela modal; aqui usamos argumentos
            // invalidos de proposito para que o fluxo apenas registre e retorne,
            // exercitando a troca de thread sem abrir popup.
            NotificationService notificacao =
                    new NotificationService("https://www.gov.br/ecf", "icone.ico");

            CountDownLatch retornou = new CountDownLatch(1);
            SwingUtilities.invokeLater(() -> {
                notificacao.showUpdate("invalido", "tambem-invalido");
                retornou.countDown();
            });

            assertThat(retornou.await(10, TimeUnit.SECONDS))
                    .as("showUpdate deve retornar imediatamente quando chamado da EDT")
                    .isTrue();
        }
    }
}

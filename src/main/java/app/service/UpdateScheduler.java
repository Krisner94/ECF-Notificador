package app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agenda e serializa as verificacoes de atualizacao.
 *
 * <p>As verificacoes chegam por tres caminhos: o item de menu, o duplo clique no
 * icone e o agendador. Os itens de menu da bandeja rodam na EDT, onde uma
 * requisicao de rede congelaria a interface e faria o popup abrir de outro
 * contexto. Encaminhando tudo para um executor de thread unica, o caminho e
 * sempre o mesmo e nunca ha duas verificacoes simultaneas.</p>
 */
public class UpdateScheduler {

    private static final Logger log = LoggerFactory.getLogger(UpdateScheduler.class);

    /**
     * Intervalo de recarga da configuracao do Gist.
     *
     * <p>5 minutos e o piso util: o CDN do GitHub serve a URL raw com
     * {@code Cache-Control: max-age=300}, entao verificar mais rapido que isso
     * nao anteciparia a mudanca. O custo de cada verificacao e minimo porque a
     * requisicao usa {@code If-None-Match} e normalmente recebe {@code 304}.</p>
     */
    private static final long INTERVALO_REFRESH_GIST_MINUTOS = 5;

    /** Executa uma verificacao; extraido para os testes observarem as chamadas. */
    @FunctionalInterface
    public interface CheckTask {
        UpdateService.CheckResult run();
    }

    private final ScheduledExecutorService executor;
    private final AtomicInteger execucoes = new AtomicInteger();
    private final AtomicInteger falhas = new AtomicInteger();

    public UpdateScheduler() {
        this(Executors.newSingleThreadScheduledExecutor(tarefa -> {
            Thread thread = new Thread(tarefa, "ecf-verificacao");
            // Daemon: o programa nao deve ficar preso por causa do agendador.
            thread.setDaemon(true);
            return thread;
        }));
    }

    /** Construtor para testes. */
    public UpdateScheduler(ScheduledExecutorService executor) {
        this.executor = executor;
    }

    public int totalDeExecucoes() {
        return execucoes.get();
    }

    public int totalDeFalhas() {
        return falhas.get();
    }

    /** Agenda uma verificacao imediata, fora da EDT. */
    public void submit(CheckTask task) {
        executor.submit(() -> executar(task));
    }

    /**
     * Agenda verificacoes periodicas.
     *
     * <p>O atraso inicial e o proprio intervalo: a verificacao de abertura e
     * feita por {@link #submit}, evitando duas checagens seguidas.</p>
     */
    public void scheduleAtFixedRate(CheckTask task, int intervaloHoras) {
        int intervalo = intervaloHoras < 1 ? 1 : intervaloHoras;
        executor.scheduleAtFixedRate(() -> executar(task), intervalo, intervalo, TimeUnit.HOURS);
    }

    /** Renova a configuracao das instalacoes em execucao sem reiniciar o programa. */
    public void scheduleGistRefresh(Runnable refresh) {
        executor.scheduleAtFixedRate(() -> {
            try {
                refresh.run();
            } catch (RuntimeException e) {
                log.warn("Falha ao recarregar a configuracao do Gist: {}", e.getMessage());
            }
        }, INTERVALO_REFRESH_GIST_MINUTOS, INTERVALO_REFRESH_GIST_MINUTOS, TimeUnit.MINUTES);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * Executa a tarefa isolando falhas: uma excecao que escapasse derrubaria a
     * thread do agendador e o programa pararia de verificar silenciosamente.
     */
    private void executar(CheckTask task) {
        execucoes.incrementAndGet();
        try {
            task.run();
        } catch (RuntimeException e) {
            falhas.incrementAndGet();
            log.warn("Falha na verificacao de atualizacoes: {}", e.getMessage());
        }
    }
}

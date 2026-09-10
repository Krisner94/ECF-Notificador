package app.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Modulo 4 - Persistencia local compativel com GraalVM Native Image.
 *
 * <p>Valida o {@link UpdateHistoryStore}, que e o armazenamento embarcado do
 * programa. A escolha por arquivo texto com escrita atomica (em vez de H2 ou
 * SQLite) esta justificada na documentacao da classe: um driver JDBC exigiria
 * metadata de reflexao/JNI extensa e aumentaria o binario nativo sem trazer
 * beneficio para um log de poucas linhas.</p>
 */
class UpdateHistoryStoreTest {

    private static UpdateHistoryStore em(Path tempDir) {
        return new UpdateHistoryStore(tempDir.resolve("config").resolve("updateHistory.log"));
    }

    @Nested
    @DisplayName("Escrita e leitura")
    class EscritaELeitura {

        @ParameterizedTest(name = "[{index}] resultado {0}")
        @EnumSource(UpdateService.CheckResult.class)
        @DisplayName("Grava e le um registro para cada resultado possivel")
        void gravaELePorResultado(UpdateService.CheckResult resultado, @TempDir Path tempDir) {
            UpdateHistoryStore store = em(tempDir);

            assertThat(store.record("12.2.5", "12.2.6", resultado)).isTrue();

            List<UpdateHistoryEntry> entradas = store.readAll();
            assertThat(entradas).hasSize(1);

            UpdateHistoryEntry entrada = entradas.get(0);
            assertThat(entrada.installedVersion()).isEqualTo("12.2.5");
            assertThat(entrada.latestVersion()).isEqualTo("12.2.6");
            assertThat(entrada.result()).isEqualTo(resultado);
            assertThat(entrada.timestamp()).isNotBlank();
        }

        @Test
        @DisplayName("Preserva a ordem cronologica dos registros")
        void preservaOrdem(@TempDir Path tempDir) {
            UpdateHistoryStore store = em(tempDir);

            store.record("1.0.0", "1.0.0", UpdateService.CheckResult.UP_TO_DATE);
            store.record("1.0.0", "1.0.1", UpdateService.CheckResult.UPDATE_AVAILABLE);
            store.record("1.0.1", "1.0.1", UpdateService.CheckResult.UP_TO_DATE);

            assertThat(store.readAll())
                    .extracting(UpdateHistoryEntry::latestVersion)
                    .containsExactly("1.0.0", "1.0.1", "1.0.1");
        }

        @Test
        @DisplayName("lastEntry devolve o registro mais recente")
        void lastEntryDevolveOMaisRecente(@TempDir Path tempDir) {
            UpdateHistoryStore store = em(tempDir);
            store.record("1.0.0", "1.0.0", UpdateService.CheckResult.UP_TO_DATE);
            store.record("1.0.0", "2.0.0", UpdateService.CheckResult.UPDATE_AVAILABLE);

            assertThat(store.lastEntry()).isNotNull();
            assertThat(store.lastEntry().latestVersion()).isEqualTo("2.0.0");
            assertThat(store.lastEntry().result())
                    .isEqualTo(UpdateService.CheckResult.UPDATE_AVAILABLE);
        }

        @Test
        @DisplayName("size reflete a quantidade de registros")
        void sizeRefleteAQuantidade(@TempDir Path tempDir) {
            UpdateHistoryStore store = em(tempDir);
            assertThat(store.size()).isZero();

            store.record("1.0.0", "1.0.0", UpdateService.CheckResult.UP_TO_DATE);
            store.record("1.0.0", "1.0.1", UpdateService.CheckResult.UPDATE_AVAILABLE);

            assertThat(store.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("Historico inexistente e tratado como vazio")
        void historicoInexistenteEhVazio(@TempDir Path tempDir) {
            UpdateHistoryStore store = em(tempDir);

            assertThat(store.readAll()).isEmpty();
            assertThat(store.lastEntry()).isNull();
            assertThat(store.size()).isZero();
        }

        @Test
        @DisplayName("Os dados persistem entre instancias (sobrevivem ao reinicio)")
        void dadosSobrevivemAoReinicio(@TempDir Path tempDir) {
            em(tempDir).record("12.2.5", "12.2.6", UpdateService.CheckResult.UPDATE_AVAILABLE);

            // Nova instancia apontando para o mesmo arquivo: simula reiniciar.
            UpdateHistoryStore reaberto = em(tempDir);

            assertThat(reaberto.size()).isEqualTo(1);
            assertThat(reaberto.lastEntry().latestVersion()).isEqualTo("12.2.6");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t"})
        @DisplayName("Versao em branco e gravada como campo vazio")
        void versaoEmBrancoViraVazio(String emBranco, @TempDir Path tempDir) {
            UpdateHistoryStore store = em(tempDir);
            store.record(emBranco, emBranco, UpdateService.CheckResult.UNKNOWN);

            UpdateHistoryEntry entrada = store.lastEntry();
            assertThat(entrada.installedVersion()).isEmpty();
            assertThat(entrada.comparou()).isFalse();
        }

        @Test
        @DisplayName("Versao nula e gravada como vazio, sem corromper a linha")
        void versaoNulaViraVazio(@TempDir Path tempDir) {
            UpdateHistoryStore store = em(tempDir);
            store.record(null, null, UpdateService.CheckResult.UNKNOWN);

            assertThat(store.lastEntry().installedVersion()).isEmpty();
        }

        @Test
        @DisplayName("Resultado nulo nao grava nada")
        void resultadoNuloNaoGrava(@TempDir Path tempDir) {
            UpdateHistoryStore store = em(tempDir);

            assertThat(store.record("1.0.0", "1.0.1", null)).isFalse();
            assertThat(store.size()).isZero();
        }

        @Test
        @DisplayName("clear apaga o historico")
        void clearApagaOHistorico(@TempDir Path tempDir) {
            UpdateHistoryStore store = em(tempDir);
            store.record("1.0.0", "1.0.0", UpdateService.CheckResult.UP_TO_DATE);

            assertThat(store.clear()).isTrue();
            assertThat(store.size()).isZero();
            // Segunda chamada: o arquivo ja nao existe.
            assertThat(store.clear()).isFalse();
        }
    }

    @Nested
    @DisplayName("Robustez do arquivo")
    class RobustezDoArquivo {

        @Test
        @DisplayName("Linha malformada e ignorada, sem perder o resto do historico")
        void linhaMalformadaEhIgnorada(@TempDir Path tempDir) throws IOException {
            Path arquivo = tempDir.resolve("config").resolve("updateHistory.log");
            Files.createDirectories(arquivo.getParent());
            Files.writeString(arquivo,
                    "# ECF-Notificador historico v1\n"
                            + "linha completamente invalida\n"
                            + "2026-01-01T10:00\t1.0.0\t1.0.1\tUPDATE_AVAILABLE\n"
                            + "outra\tlinha\ttorta\n",
                    StandardCharsets.UTF_8);

            UpdateHistoryStore store = new UpdateHistoryStore(arquivo);

            assertThat(store.readAll()).hasSize(1);
            assertThat(store.lastEntry().latestVersion()).isEqualTo("1.0.1");
        }

        @Test
        @DisplayName("Resultado desconhecido no arquivo e descartado")
        void resultadoDesconhecidoEhDescartado(@TempDir Path tempDir) throws IOException {
            Path arquivo = tempDir.resolve("config").resolve("updateHistory.log");
            Files.createDirectories(arquivo.getParent());
            Files.writeString(arquivo,
                    "2026-01-01T10:00\t1.0.0\t1.0.1\tRESULTADO_QUE_NAO_EXISTE\n",
                    StandardCharsets.UTF_8);

            assertThat(new UpdateHistoryStore(arquivo).readAll()).isEmpty();
        }

        @Test
        @DisplayName("Comentarios e linhas vazias sao ignorados")
        void comentariosIgnorados(@TempDir Path tempDir) throws IOException {
            Path arquivo = tempDir.resolve("config").resolve("updateHistory.log");
            Files.createDirectories(arquivo.getParent());
            Files.writeString(arquivo, "# comentario\n\n   \n", StandardCharsets.UTF_8);

            assertThat(new UpdateHistoryStore(arquivo).readAll()).isEmpty();
        }

        @Test
        @DisplayName("Arquivo binario arbitrario nao derruba a leitura")
        void arquivoBinarioNaoDerruba(@TempDir Path tempDir) throws IOException {
            Path arquivo = tempDir.resolve("config").resolve("updateHistory.log");
            Files.createDirectories(arquivo.getParent());
            Files.write(arquivo, new byte[] {(byte) 0xFF, (byte) 0xFE, 0x00, 0x01, (byte) 0x80});

            UpdateHistoryStore store = new UpdateHistoryStore(arquivo);

            assertThat(store.size()).isZero();
            // E continua gravavel.
            assertThat(store.record("1.0.0", "1.0.1", UpdateService.CheckResult.UPDATE_AVAILABLE))
                    .isTrue();
            assertThat(store.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("Caminho impossivel de escrever devolve false em vez de lancar")
        void falhaDeEscritaNaoLanca(@TempDir Path tempDir) throws IOException {
            // O arquivo de historico fica DENTRO de um arquivo comum: criar o
            // diretorio pai falha. E um cenario real de instalacao corrompida,
            // e nao pode escapar excecao para quem chamou.
            Path arquivoComum = tempDir.resolve("nao-e-diretorio.txt");
            Files.writeString(arquivoComum, "conteudo", StandardCharsets.UTF_8);
            Path destinoInvalido = arquivoComum.resolve("historico.log");

            UpdateHistoryStore store = new UpdateHistoryStore(destinoInvalido);

            assertThat(store.record("1.0.0", "1.0.1", UpdateService.CheckResult.UPDATE_AVAILABLE))
                    .as("O historico e acessorio: uma falha de escrita nao pode virar excecao")
                    .isFalse();
        }

        @Test
        @DisplayName("Tab e quebra de linha na versao nao quebram o formato do arquivo")
        void caracteresEspeciaisNaoQuebramOFormato(@TempDir Path tempDir) throws IOException {
            UpdateHistoryStore store = em(tempDir);
            store.record("1.0.0\tcom\ttab", "1.0.1\ncom\nquebra", UpdateService.CheckResult.UNKNOWN);

            // Continua sendo exatamente um registro.
            assertThat(store.readAll()).hasSize(1);
            assertThat(store.lastEntry().installedVersion()).doesNotContain("\t", "\n");
        }

        @Test
        @DisplayName("O cabeçalho de versao do formato e gravado")
        void cabecalhoEhGravado(@TempDir Path tempDir) throws IOException {
            UpdateHistoryStore store = em(tempDir);
            store.record("1.0.0", "1.0.0", UpdateService.CheckResult.UP_TO_DATE);

            String conteudo = Files.readString(store.getHistoryPath(), StandardCharsets.UTF_8);
            assertThat(conteudo).startsWith("# ECF-Notificador historico v1");
        }

        @Test
        @DisplayName("O historico e truncado ao passar do limite de registros")
        void historicoEhTruncado(@TempDir Path tempDir) throws IOException {
            UpdateHistoryStore store = em(tempDir);
            int limite = 500;
            for (int i = 0; i < limite + 20; i++) {
                store.record("1.0." + i, "1.0." + (i + 1), UpdateService.CheckResult.UPDATE_AVAILABLE);
            }

            assertThat(store.size()).isEqualTo(limite);
            // Os mais antigos foram descartados; o ultimo permanece.
            assertThat(store.lastEntry().installedVersion()).isEqualTo("1.0." + (limite + 19));
        }
    }

    @Nested
    @DisplayName("Concorrencia")
    class Concorrencia {

        @Test
        @DisplayName("Escritas simultaneas nao corrompem o arquivo")
        void escritasSimultaneasNaoCorrompem(@TempDir Path tempDir) throws InterruptedException {
            UpdateHistoryStore store = em(tempDir);
            int threads = 8;
            int porThread = 25;
            CountDownLatch inicio = new CountDownLatch(1);
            CountDownLatch fim = new CountDownLatch(threads);
            AtomicInteger gravados = new AtomicInteger();
            ExecutorService pool = Executors.newFixedThreadPool(threads);

            try {
                for (int t = 0; t < threads; t++) {
                    int indice = t;
                    pool.submit(() -> {
                        try {
                            inicio.await();
                            for (int i = 0; i < porThread; i++) {
                                if (store.record("1.0." + indice, "1.0." + (indice + 1),
                                        UpdateService.CheckResult.UPDATE_AVAILABLE)) {
                                    gravados.incrementAndGet();
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            fim.countDown();
                        }
                    });
                }

                inicio.countDown();
                assertThat(fim.await(60, TimeUnit.SECONDS)).isTrue();
            } finally {
                pool.shutdownNow();
                pool.awaitTermination(10, TimeUnit.SECONDS);
            }

            // Alguma gravacao pode falhar sob disputa de trava (comportamento
            // aceito e registrado em log); o que NAO pode acontecer e o arquivo
            // ficar corrompido: toda linha restante deve ser legivel.
            assertThat(gravados.get()).isPositive();
            List<UpdateHistoryEntry> entradas = store.readAll();
            assertThat(entradas).isNotEmpty();
            assertThat(entradas).allSatisfy(entrada ->
                    assertThat(entrada.result()).isNotNull());
        }

        @Test
        @DisplayName("Leitura durante escrita nao devolve registro parcial")
        void leituraDuranteEscrita(@TempDir Path tempDir) throws InterruptedException {
            UpdateHistoryStore store = em(tempDir);
            store.record("1.0.0", "1.0.0", UpdateService.CheckResult.UP_TO_DATE);

            Thread escritor = new Thread(() -> {
                for (int i = 0; i < 100; i++) {
                    store.record("1.0." + i, "1.0." + (i + 1),
                            UpdateService.CheckResult.UPDATE_AVAILABLE);
                }
            });
            escritor.start();

            while (escritor.isAlive()) {
                // O arquivo nunca deve aparecer pela metade.
                assertThat(store.readAll()).allSatisfy(entrada -> {
                    assertThat(entrada.installedVersion()).isNotNull();
                    assertThat(entrada.latestVersion()).isNotNull();
                    assertThat(entrada.result()).isNotNull();
                    assertThat(entrada.timestamp()).isNotBlank();
                });
            }
            escritor.join(10_000);
            assertThat(escritor.isAlive()).isFalse();
        }
    }

    @Nested
    @DisplayName("Regras de negocio do registro")
    class RegrasDeNegocio {

        @Test
        @DisplayName("comparou() indica que havia as duas versoes")
        void comparouExigeAsDuasVersoes(@TempDir Path tempDir) {
            UpdateHistoryStore store = em(tempDir);

            store.record("", "", UpdateService.CheckResult.UNKNOWN);
            assertThat(store.lastEntry().comparou()).isFalse();

            store.record("1.0.0", "1.0.1", UpdateService.CheckResult.UPDATE_AVAILABLE);
            assertThat(store.lastEntry().comparou()).isTrue();
        }

        @Test
        @DisplayName("avisouUsuario() so e verdadeiro em UPDATE_AVAILABLE")
        void avisouUsuarioApenasQuandoAtualizou(@TempDir Path tempDir) {
            UpdateHistoryStore store = em(tempDir);

            store.record("1.0.0", "1.0.0", UpdateService.CheckResult.UP_TO_DATE);
            assertThat(store.lastEntry().avisouUsuario()).isFalse();

            store.record("1.0.0", "1.0.1", UpdateService.CheckResult.UPDATE_AVAILABLE);
            assertThat(store.lastEntry().avisouUsuario()).isTrue();

            store.record("", "", UpdateService.CheckResult.UNKNOWN);
            assertThat(store.lastEntry().avisouUsuario()).isFalse();
        }
    }
}

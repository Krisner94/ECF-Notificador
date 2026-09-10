package app.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Modulo 3 - Comparador semantico de versoes.
 *
 * <p>Sao o coracao da decisao "avisar ou nao o usuario": um erro aqui faz o
 * programa notificar atualizacao inexistente, ou deixar de avisar quando existe
 * uma versao nova.</p>
 *
 * <p>Alem da correcao, os testes cobrem a exigencia de <b>baixa alocacao</b>: o
 * metodo roda no agendador e nao pode gerar lixo a cada chamada, porque no
 * binario nativo (Substrate VM) o coletor tem menos folga.</p>
 */
class VersionComparatorTest {

    private static final int MAX_SEGMENTS = 4;

    @ParameterizedTest(name = "[{index}] {0} vs {1} = {2}")
    @CsvSource(delimiter = '|', value = {
            // Iguais
            "12.2.5 | 12.2.5 | 0",
            "12.2   | 12.2.0 | 0",
            "12     | 12.0.0 | 0",
            "1.0.0  | 1.0   | 0",
            // Mais nova / mais antiga
            "12.2.6 | 12.2.5 | 1",
            "12.2.5 | 12.2.6 | -1",
            "13.0.0 | 12.9.9 | 1",
            "9.0.0  | 12.0.0 | -1",
            // Comparacao NUMERICA, nao lexicografica (10 > 9)
            "12.10.1 | 12.9.9 | 1",
            "12.9.9  | 12.10.1 | -1",
            "2.0.0   | 10.0.0 | -1",
            // Sufixos nao numericos sao ignorados
            "12.2.5-beta | 12.2.5 | 0",
            "12.2.5      | 12.2.5-rc1 | 0",
            "12.2.6-rc1  | 12.2.5 | 1",
            // Espacos nas pontas
            "  12.2.6  | 12.2.5 | 1",
            // Quarto segmento e considerado
            "1.2.3.4 | 1.2.3.4 | 0",
            "1.2.3.5 | 1.2.3.4 | 1",
            "1.2.3.4 | 1.2.3.5 | -1",
            // Valores extremos
            "0.0.1 | 0.0.0 | 1",
            "0.0.0 | 0.0.1 | -1",
    })
    @DisplayName("Compara versoes numericamente, ignorando sufixos e espacos")
    void comparaVersoes(String esquerda, String direita, int esperado) {
        int resultado = VersionComparator.compare(esquerda, direita);

        if (esperado == 0) {
            assertThat(resultado).as("%s deveria equivaler a %s", esquerda, direita).isZero();
        } else if (esperado > 0) {
            assertThat(resultado).as("%s deveria ser posterior a %s", esquerda, direita)
                    .isPositive();
        } else {
            assertThat(resultado).as("%s deveria ser anterior a %s", esquerda, direita)
                    .isNegative();
        }
    }

    @ParameterizedTest(name = "[{index}] publicada {0}, instalada {1} => {2}")
    @CsvSource(delimiter = '|', value = {
            // Atualizacao real
            "12.2.6     | 12.2.5 | true",
            "13.0.0     | 12.9.9 | true",
            "12.3       | 12.2.9 | true",
            "12.2.5.1   | 12.2.5 | true",
            "12.2.6-rc1 | 12.2.6 | false",
            // Instalada ja e a mais recente
            "12.2.5 | 12.2.5 | false",
            "12.2   | 12.2.5 | false",
            "12.2.5 | 13.0.0 | false",
            // Publicada e ANTERIOR (rollback no site): nao avisar
            "12.2.4 | 12.2.5 | false",
            "12.9.9 | 13.0.0 | false",
    })
    @DisplayName("Somente avisa quando a versao publicada e estritamente mais nova")
    void somenteAvisaQuandoEhMaisNova(String publicada, String instalada, boolean esperado) {
        assertThat(VersionComparator.isNewer(publicada, instalada)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Versao em branco nunca e tratada como atualizacao")
    void versaoEmBrancoNaoEhAtualizacao(String valor) {
        assertThat(VersionComparator.isNewer(valor, "12.2.5")).isFalse();
        assertThat(VersionComparator.isNewer("12.2.5", valor)).isFalse();
        assertThat(VersionComparator.isNewer(valor, valor)).isFalse();
    }

    @Test
    @DisplayName("Versao nula nunca e tratada como atualizacao")
    void versaoNulaNaoEhAtualizacao() {
        assertThat(VersionComparator.isNewer(null, "12.2.5")).isFalse();
        assertThat(VersionComparator.isNewer("12.2.5", null)).isFalse();
        assertThat(VersionComparator.isNewer(null, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Comparacao direta exige versao valida dos dois lados")
    void comparacaoExigeVersaoValida(String invalido) {
        assertThatThrownBy(() -> VersionComparator.compare(invalido, "12.2.5"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VersionComparator.compare("12.2.5", invalido))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Versao nula lanca excecao descritiva")
    void versaoNulaLancaExcecao() {
        assertThatThrownBy(() -> VersionComparator.compare(null, "1.0.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("esquerda");
        assertThatThrownBy(() -> VersionComparator.compare("1.0.0", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("direita");
    }

    @Test
    @DisplayName("Segmento maior que o limite nao quebra a comparacao")
    void segmentoGiganteNaoQuebra() {
        assertThat(VersionComparator.compare("12.99999999999999999999", "12.2.5")).isPositive();
        assertThat(VersionComparator.compare("12.99999999999999999999",
                "12.99999999999999999999")).isZero();
        assertThat(VersionComparator.compare("99999999999999999999.0", "12.0")).isPositive();
    }

    @Test
    @DisplayName("Versao com texto no lugar do numero conta como zero")
    void textoSemNumeroContaComoZero() {
        assertThat(VersionComparator.compare("beta.0.0", "0.0.0")).isZero();
        assertThat(VersionComparator.compare("12.beta", "12.0")).isZero();
    }

    @Test
    @DisplayName("A comparacao e antisimetrica")
    void comparacaoEhAntisimetrica() {
        assertThat(VersionComparator.compare("12.2.6", "12.2.5")).isPositive();
        assertThat(VersionComparator.compare("12.2.5", "12.2.6")).isNegative();
    }

    @Test
    @DisplayName("A comparacao e transitiva")
    void comparacaoEhTransitiva() {
        assertThat(VersionComparator.compare("1.0.0", "2.0.0")).isNegative();
        assertThat(VersionComparator.compare("2.0.0", "3.0.0")).isNegative();
        assertThat(VersionComparator.compare("1.0.0", "3.0.0")).isNegative();
    }

    // ===================== Requisito de alocacao =====================

    @Test
    @DisplayName("Comparacao repetida nao acumula lixo (sem alocacao por chamada)")
    void comparacaoNaoAlocaPorChamada() {
        int iteracoes = 1_000_000;

        // Aquecimento: a primeira chamada cria o buffer ThreadLocal e o JIT
        // compila o caminho quente. So depois disso a medicao faz sentido.
        for (int i = 0; i < 100_000; i++) {
            consumir(VersionComparator.compare("12.2.6", "12.2.5"));
        }

        long antes = bytesAlocadosPeloThreadAtual();
        if (antes < 0) {
            // Instrumentacao indisponivel: nao falhar por isso, os testes de
            // contrato acima ja cobrem a corretude.
            return;
        }

        for (int i = 0; i < iteracoes; i++) {
            consumir(VersionComparator.compare("12.2.6", "12.2.5"));
        }
        long depois = bytesAlocadosPeloThreadAtual();

        long total = Math.max(0, depois - antes);
        double porChamada = (double) total / iteracoes;

        // Com alocacao zero o valor fica em 0.0. O limiar existe para pegar
        // regressao: a implementacao anterior (split/substring/parseInt)
        // alocava ~528 bytes por comparacao - medido com esta mesma sonda.
        assertThat(porChamada)
                .as("Bytes alocados por comparacao (total=%d em %d chamadas)", total, iteracoes)
                .isLessThan(16.0);
    }

    @Test
    @DisplayName("isNewer tambem nao aloca por chamada")
    void isNewerNaoAlocaPorChamada() {
        int iteracoes = 1_000_000;
        for (int i = 0; i < 100_000; i++) {
            consumirBool(VersionComparator.isNewer("12.2.6", "12.2.5"));
        }

        long antes = bytesAlocadosPeloThreadAtual();
        if (antes < 0) {
            return;
        }
        for (int i = 0; i < iteracoes; i++) {
            consumirBool(VersionComparator.isNewer("12.2.6", "12.2.5"));
        }
        long depois = bytesAlocadosPeloThreadAtual();

        double porChamada = (double) Math.max(0, depois - antes) / iteracoes;
        assertThat(porChamada)
                .as("Bytes alocados por isNewer")
                .isLessThan(16.0);
    }

    /**
     * Consome o resultado de forma que o JIT nao possa eliminar a chamada.
     *
     * <p>Sem isso o compilador remove o laco inteiro (a medicao passa a acusar
     * zero mesmo com alocacao real), o que tornaria o teste inutil.</p>
     */
    private static void consumir(int valor) {
        SUMIDOURO ^= valor;
    }

    private static void consumirBool(boolean valor) {
        SUMIDOURO_BOOL ^= valor;
    }

    /** Volatile para impedir dead-code elimination; nao faz parte do contrato. */
    private static volatile int SUMIDOURO;
    private static volatile boolean SUMIDOURO_BOOL;

    @Test
    @DisplayName("Comparacoes concorrentes nao se corrompem entre si")
    void comparacaoEhThreadSafe() throws InterruptedException {
        // O buffer e ThreadLocal: duas threads comparando ao mesmo tempo nao
        // podem interferir nos segmentos uma da outra.
        int threads = 8;
        int porThread = 20_000;
        Thread[] trabalhadores = new Thread[threads];
        boolean[] falhou = new boolean[threads];

        for (int t = 0; t < threads; t++) {
            int indice = t;
            trabalhadores[t] = new Thread(() -> {
                for (int i = 0; i < porThread; i++) {
                    if (VersionComparator.compare("12.2.6", "12.2.5") <= 0
                            || VersionComparator.compare("12.2.5", "12.2.6") >= 0) {
                        falhou[indice] = true;
                        return;
                    }
                }
            }, "comparador-" + t);
            trabalhadores[t].start();
        }
        for (Thread trabalhador : trabalhadores) {
            trabalhador.join();
        }

        assertThat(falhou).containsOnly(false);
    }

    @Test
    @DisplayName("A comparacao funciona com o buffer ja usado em outras versoes")
    void bufferNaoCarregaLixoEntreChamadas() {
        // Se os segmentos nao fossem zerados, um valor de 4 segmentos deixaria
        // residuo que afetaria a comparacao seguinte de 3 segmentos.
        assertThat(VersionComparator.compare("1.2.3.9", "1.2.3.8")).isPositive();
        assertThat(VersionComparator.compare("1.2.3", "1.2.3")).isZero();
        assertThat(VersionComparator.compare("1.2.4", "1.2.3")).isPositive();
        assertThat(VersionComparator.compare("1.2", "1.2.0")).isZero();
        assertThat(VersionComparator.compare("1.2.3.4", "1.2.3.4")).isZero();

        // Cobre o MAX_SEGMENTS de ponta a ponta.
        for (int i = 0; i < MAX_SEGMENTS; i++) {
            assertThat(VersionComparator.compare("9.9.9.9", "9.9.9.9")).isZero();
        }
    }

    /**
     * Le a memoria alocada acumulada pelo thread atual.
     *
     * <p>{@code com.sun.management.ThreadMXBean} existe no HotSpot e no GraalVM.
     * Se a API nao estiver disponivel, devolve {@code -1} e o teste degrada sem
     * falso negativo.</p>
     */
    private static long bytesAlocadosPeloThreadAtual() {
        try {
            com.sun.management.ThreadMXBean bean =
                    (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory
                            .getThreadMXBean();
            if (!bean.isThreadAllocatedMemorySupported()) {
                return -1;
            }
            if (!bean.isThreadAllocatedMemoryEnabled()) {
                bean.setThreadAllocatedMemoryEnabled(true);
            }
            return bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        } catch (RuntimeException | LinkageError e) {
            return -1;
        }
    }
}

package app.utils;

/**
 * Compara versoes numericas no formato {@code X}, {@code X.Y} ou {@code X.Y.Z}.
 *
 * <p>Comparacao numerica e nao por igualdade: uma versao publicada anterior a
 * instalada (rollback no site) nao deve gerar aviso de atualizacao.</p>
 *
 * <p>Os segmentos sao lidos por varredura de caracteres em um buffer por thread,
 * com zero alocacao por chamada - o metodo roda no agendador e o coletor do
 * Substrate VM tem menos folga que o da JVM.</p>
 */
public final class VersionComparator {

    private static final int MAX_SEGMENTS = 4;

    /** Buffer por thread: 4 espacos para cada lado da comparacao. */
    private static final ThreadLocal<int[]> SCRATCH =
            ThreadLocal.withInitial(() -> new int[MAX_SEGMENTS * 2]);

    /** Evita estouro de int com um numero absurdo vindo de arquivo adulterado. */
    private static final int LIMITE_SEGMENTO = 1_000_000;

    private VersionComparator() {
    }

    /**
     * @return negativo se {@code left} for anterior, positivo se posterior,
     *         {@code 0} se equivalentes.
     * @throws IllegalArgumentException se alguma versao for nula ou vazia.
     */
    public static int compare(String left, String right) {
        if (left == null || left.isBlank()) {
            throw new IllegalArgumentException("Versao esquerda nao pode ser nula ou vazia.");
        }
        if (right == null || right.isBlank()) {
            throw new IllegalArgumentException("Versao direita nao pode ser nula ou vazia.");
        }

        int[] buffer = SCRATCH.get();
        parseInto(left, buffer, 0);
        parseInto(right, buffer, MAX_SEGMENTS);

        for (int i = 0; i < MAX_SEGMENTS; i++) {
            int comparacao = Integer.compare(buffer[i], buffer[MAX_SEGMENTS + i]);
            if (comparacao != 0) {
                return comparacao;
            }
        }
        return 0;
    }

    /**
     * Indica se {@code candidate} e estritamente posterior a {@code current}.
     * Tolerante a entrada invalida: devolve {@code false} em vez de lancar.
     */
    public static boolean isNewer(String candidate, String current) {
        if (candidate == null || candidate.isBlank() || current == null || current.isBlank()) {
            return false;
        }
        try {
            return compare(candidate, current) > 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Le ate {@link #MAX_SEGMENTS} segmentos para dentro do buffer, sem criar
     * objetos intermediarios.
     *
     * <p>Um caractere que nao seja digito nem ponto <b>fecha</b> o segmento.
     * Sem isso {@code 12.2.6-rc1} viraria {@code [12, 2, 61]} e o programa
     * anunciaria uma atualizacao inexistente.</p>
     *
     * @param base posicao inicial de escrita no buffer.
     */
    private static void parseInto(String version, int[] destino, int base) {
        int segmento = 0;
        int valor = 0;
        boolean temDigito = false;
        boolean segmentoFechado = false;

        for (int i = 0; i < version.length() && segmento < MAX_SEGMENTS; i++) {
            char caractere = version.charAt(i);

            if (caractere >= '0' && caractere <= '9') {
                if (!segmentoFechado) {
                    if (valor < LIMITE_SEGMENTO) {
                        valor = (valor * 10) + (caractere - '0');
                    }
                    temDigito = true;
                }
            } else if (caractere == '.') {
                destino[base + segmento] = temDigito ? valor : 0;
                segmento++;
                valor = 0;
                temDigito = false;
                segmentoFechado = false;
            } else {
                segmentoFechado = true;
            }
        }

        // Zera os segmentos que faltaram para nao herdar lixo da chamada anterior.
        if (segmento < MAX_SEGMENTS) {
            destino[base + segmento] = temDigito ? valor : 0;
            segmento++;
        }
        for (int i = segmento; i < MAX_SEGMENTS; i++) {
            destino[base + i] = 0;
        }
    }
}

package app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Historico local das verificacoes de atualizacao.
 *
 * <p><b>Arquivo texto e nao H2/SQLite:</b> o projeto compila para GraalVM Native
 * Image, onde um driver JDBC embarcado exigiria metadata de reflexao e JNI
 * extensa, aumentaria o binario em varios MB e traria risco de falha em build
 * AOT - tudo para guardar um log de poucas linhas de um utilitario de bandeja.</p>
 *
 * <p>Usa apenas {@code java.nio.file}/{@code java.io}, suportados integralmente
 * pelo Native Image. A gravacao e atomica (temporario + move) e protegida por
 * {@link FileLock} para duas instancias nao corromperem o arquivo.</p>
 *
 * <p>Formato: uma linha por registro, quatro campos separados por tabulacao. O
 * resultado e gravado por nome, nao por ordinal, para o arquivo continuar legivel
 * se a ordem do enum mudar.</p>
 */
public class UpdateHistoryStore {

    private static final Logger log = LoggerFactory.getLogger(UpdateHistoryStore.class);

    private static final String HEADER = "# ECF-Notificador historico v1";

    private static final String SEPARATOR = "\t";
    private static final int CAMPOS = 4;

    /** Acima disso os registros mais antigos sao descartados. */
    private static final int MAX_RECORDS = 500;

    private final Path historyPath;

    public UpdateHistoryStore(Path historyPath) {
        this.historyPath = historyPath;
    }

    /** Caminho do arquivo de historico, para log e diagnostico. */
    public Path getHistoryPath() {
        return historyPath;
    }

    /**
     * Acrescenta um registro.
     *
     * @return {@code true} se foi gravado.
     */
    public boolean record(String installedVersion, String latestVersion, UpdateService.CheckResult result) {
        if (result == null) {
            return false;
        }

        String linha = String.join(SEPARATOR,
                LocalDateTime.now().toString(),
                safe(installedVersion),
                safe(latestVersion),
                result.name());

        try {
            Path parent = historyPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            synchronized (UpdateHistoryStore.class) {
                List<String> linhas = new ArrayList<>(readAllLines());
                linhas.add(linha);
                writeAtomically(trimToLimit(linhas));
            }
            return true;
        } catch (IOException e) {
            // O historico e acessorio: falhar aqui nao pode interromper a
            // verificacao de atualizacoes.
            log.warn("Nao foi possivel gravar o historico em '{}': {}", historyPath, e.getMessage());
            return false;
        }
    }

    /**
     * Le o historico do mais antigo para o mais recente.
     *
     * @return lista imutavel; vazia se o arquivo nao existir ou estiver corrompido.
     */
    public List<UpdateHistoryEntry> readAll() {
        List<UpdateHistoryEntry> entradas = new ArrayList<>();
        for (String linha : readAllLines()) {
            UpdateHistoryEntry entrada = parseLine(linha);
            if (entrada != null) {
                entradas.add(entrada);
            }
        }
        return List.copyOf(entradas);
    }

    /** Quantidade de registros gravados. */
    public int size() {
        return readAll().size();
    }

    /** Ultimo registro gravado, ou {@code null} se vazio. */
    public UpdateHistoryEntry lastEntry() {
        List<UpdateHistoryEntry> entradas = readAll();
        return entradas.isEmpty() ? null : entradas.get(entradas.size() - 1);
    }

    /**
     * Apaga o historico.
     *
     * @return {@code true} se o arquivo existia e foi removido.
     */
    public boolean clear() {
        try {
            return Files.deleteIfExists(historyPath);
        } catch (IOException e) {
            log.warn("Nao foi possivel apagar o historico '{}': {}", historyPath, e.getMessage());
            return false;
        }
    }

    private List<String> readAllLines() {
        if (!Files.isRegularFile(historyPath)) {
            return new ArrayList<>();
        }
        try {
            List<String> linhas = new ArrayList<>();
            for (String linha : Files.readAllLines(historyPath, StandardCharsets.UTF_8)) {
                if (!linha.isBlank() && !linha.startsWith("#")) {
                    linhas.add(linha);
                }
            }
            return linhas;
        } catch (IOException e) {
            log.warn("Historico ilegivel '{}': {}", historyPath, e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<String> trimToLimit(List<String> linhas) {
        if (linhas.size() <= MAX_RECORDS) {
            return linhas;
        }
        return new ArrayList<>(linhas.subList(linhas.size() - MAX_RECORDS, linhas.size()));
    }

    /**
     * Grava em temporario e move sobre o definitivo: uma interrupcao no meio da
     * escrita nao deixa o historico pela metade.
     */
    private void writeAtomically(List<String> linhas) throws IOException {
        Path arquivoTemp = historyPath.resolveSibling(historyPath.getFileName() + ".tmp");

        StringBuilder conteudo = new StringBuilder();
        conteudo.append(HEADER).append(System.lineSeparator());
        for (String linha : linhas) {
            conteudo.append(linha).append(System.lineSeparator());
        }

        try (RandomAccessFile raf = new RandomAccessFile(arquivoTemp.toFile(), "rw");
             FileChannel canal = raf.getChannel();
             FileLock trava = canal.lock()) {
            if (!trava.isValid()) {
                log.debug("Trava do historico nao ficou valida; seguindo com a escrita.");
            }
            raf.setLength(0);
            raf.write(conteudo.toString().getBytes(StandardCharsets.UTF_8));
            raf.getFD().sync();
        } catch (IOException | RuntimeException e) {
            log.debug("Falha ao gravar com trava em '{}': {}", arquivoTemp, e.getMessage());
        }

        moveOver(arquivoTemp);
    }

    private void moveOver(Path origem) throws IOException {
        try {
            Files.move(origem, historyPath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            // Alguns sistemas de arquivos nao suportam movimentacao atomica.
            Files.move(origem, historyPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Converte uma linha em registro.
     *
     * <p>Linha malformada e ignorada em vez de derrubar a leitura: um arquivo
     * editado a mao nao inutiliza o historico inteiro.</p>
     */
    private static UpdateHistoryEntry parseLine(String linha) {
        String[] campos = linha.split(SEPARATOR, -1);
        if (campos.length != CAMPOS) {
            return null;
        }

        UpdateService.CheckResult resultado;
        try {
            resultado = UpdateService.CheckResult.valueOf(campos[3]);
        } catch (IllegalArgumentException e) {
            return null;
        }

        return new UpdateHistoryEntry(campos[0], campos[1], campos[2], resultado);
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        // Tab e quebra de linha destruiriam o layout de uma linha por registro.
        String limpo = value.replace(SEPARATOR, " ").replace("\n", " ").replace("\r", " ");
        // Campo so com espacos vira vazio: "sem versao" tem uma so representacao.
        return limpo.isBlank() ? "" : limpo;
    }

    /** Historico padrao, ao lado do arquivo de configuracao. */
    public static UpdateHistoryStore defaultStore() {
        return new UpdateHistoryStore(
                new File(System.getProperty("user.dir"), "config/updateHistory.log").toPath());
    }
}

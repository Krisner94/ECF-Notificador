package app.testutil;

import app.config.AppConfig;
import app.service.GistSettingsLoader;

/**
 * Carregador de Gist controlado pelo teste.
 *
 * <p>Simula os dois cenarios que importam: Gist acessivel (devolve a
 * configuracao informada) e Gist indisponivel (devolve {@code null}). Nenhuma
 * requisicao de rede e feita.</p>
 */
public class StubGistLoader extends GistSettingsLoader {

    /** URL apenas simbolica: o metodo {@code load()} e sobrescrito. */
    private static final String URLA_FICTICIA = "https://gist.invalid/appSettings.json";

    private final AppConfig configuravel;

    /**
     * @param configuravel configuracao que o "Gist" devolve, ou {@code null}
     *                     para representar um Gist fora do ar.
     */
    public StubGistLoader(AppConfig configuravel) {
        super(URLA_FICTICIA);
        this.configuravel = configuravel;
    }

    @Override
    public AppConfig load() {
        return configuravel;
    }

    /** Instancia configurada para simular um Gist indisponivel. */
    public static StubGistLoader indisponivel() {
        return new StubGistLoader(null);
    }

    /** Instancia que devolve a configuracao montada a partir do JSON. */
    public static StubGistLoader comJson(String json) {
        return new StubGistLoader(TestConfigs.fromJson(json));
    }
}

# 🧬 Compilação Nativa (GraalVM Native Image)

Este documento explica como gerar o **executável nativo** (`.exe` sem JVM) do ECF-Notificador, e — mais importante — **as limitações que você precisa conhecer antes de usar**.

O alvo do projeto é **Java 25**, e o binário nativo também é gerado para Java 25. Isso significa que **tanto a compilação do `.jar` quanto a do `.exe` exigem JDK 25**; o `.exe` exige adicionalmente que esse JDK seja o **GraalVM**.

---

## ⚠️ Leia isto primeiro: limitação de interface gráfica

O ECF-Notificador usa **AWT/Swing** em alguns pontos:

| Uso | Onde | Depende de AWT? |
|---|---|---|
| `SystemTray` + `TrayIcon` | Ícone na bandeja do sistema | ✅ Sim |
| `JDialog` + `JOptionPane` | Tela de Configurações | ✅ Sim |
| `TaskDialog` (JNA/Win32) | Popup de atualização | ❌ **Não** — é nativo do Windows |
| `java.awt.Desktop` | Abrir a página de download | ✅ Sim (só ao aceitar o popup) |

O módulo **`java.desktop` não tem suporte oficial no Native Image**. Na prática:

- ❌ A bandeja do sistema (`SystemTray`/`TrayIcon`) **não funciona** em binário nativo.
- ❌ A janela de Configurações em Swing **não funciona**.
- ✅ Toda a lógica **sem interface** funciona: leitura do Gist, HTTP, comparação de versões, e — crucialmente — o **popup nativo do Windows**, que é feito via **JNA + Win32 (`TaskDialog`)**, não via Swing.

> **Conclusão prática:** o binário nativo exibe a **notificação nativa de atualização** (o propósito principal do programa), mas **não** a bandeja nem a tela de configurações. Por isso a distribuição recomendada continua sendo o fat JAR, a menos que você queira apenas o aviso de atualização.

### Por que o pipeline não falha por causa disso

Os jobs `nativo` (em `ci.yml`) e `native-release` (em `master.yml`) usam `continue-on-error: true`. Assim a pipeline **tenta** gerar o binário e mostra o resultado, mas **não deixa o build vermelho** enquanto essa limitação existir. Quando o binário passar a ser obrigatório, remova essa linha.

---

## 🛠️ Pré-requisito: GraalVM for JDK 25 (não serve OpenJDK comum)

O `native-image` **só existe no GraalVM**. Um JDK comum (Temurin, Oracle JDK, Corretto) **não** consegue gerar binário nativo.

### Instalação

1. Baixe o **GraalVM for JDK 25**: <https://www.graalvm.org/downloads/>
2. Extraia em `C:\Users\Rhama\.jdks\graalvm-jdk-25`
3. Aponte o `JAVA_HOME`:

```powershell
$env:JAVA_HOME = "C:\Users\Rhama\.jdks\graalvm-jdk-25"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

4. Confirme que o ambiente está correto — **as duas versões precisam reportar 25**:

```powershell
java -version          # deve dizer 25
native-image --version # deve dizer 25 e citar o GraalVM
```

> No CI isso é automático: a action `graalvm/setup-graalvm@v1` instala o GraalVM 25 com `native-image`.

---

## 🚀 Como gerar o binário

```bash
mvn -Pnative -DskipTests package
```

Saída: `target/ECF-Notificador.exe`

### O que o perfil `native` já configura

| Configuração | Motivo |
|---|---|
| `maven.compiler.release=25` | Bytecode e API em Java 25 |
| `--no-fallback` | Impede gerar uma imagem que ainda depende de JVM — sem isso o build pode "passar" e produzir um binário inútil |
| `--enable-url-protocols=https` | O `HttpClient` chama o Gist e o site da RFB por HTTPS |
| `metadataRepository.enabled` | Usa a metadata de reachability pública do GraalVM (Jackson/JNA) |
| `<imageName>ECF-Notificador</imageName>` | Nome final do `.exe` |

---

## 🧪 O binário roda sem JVM?

Sim — é o ponto do Native Image. Para confirmar:

```powershell
.\target\ECF-Notificador.exe
```

Se rodar numa máquina **sem Java instalado**, está correto.

---

## 📦 Metadata de reachability (reflection / JNA / recursos)

O Native Image usa *closed-world assumption*: só entra no binário o que a análise estática alcança. Acesso dinâmico (reflection, JNI, recursos) precisa de metadata.

O projeto usa:
- **Jackson** → reflection para preencher `AppConfig`/`EcfConfig`/`SettingsData`
- **JNA** → FFI/JNI para chamar a Win32 (`TaskDialog`, `GetSysColor`)
- **JSoup** → parser do HTML da Receita Federal (ver nota abaixo)

### O que já está no repositório

O perfil `native` habilita a **metadata compartilhada** do GraalVM
(`metadataRepository`), que cobre Jackson e JNA sem manutenção manual.

Além disso, o projeto versiona a metadata das **próprias classes** em:

```
src/main/resources/META-INF/native-image/app/ecf-notificador/reachability-metadata.json
```

Esse arquivo registra `AppConfig`, `EcfConfig`, `SettingsData`, o enum `Message`
e `UpdateService$CheckResult` para reflection.

> **Nota:** a configuração **não** é mais um recurso embutido no binário. Ela vem
do Gist em tempo de execução, então não há `appSettings.json` para declarar em
`resources` na metadata. O JSON baixado é desserializado pelas mesmas classes
registradas acima.

### Sobre o JSoup (verificação feita, não suposição)

Havia a expectativa de que o JSoup exigisse registro de reflection. **Foi
verificado que não:**

| Item | Resultado da verificação |
|---|---|
| Arquivos de recurso no `jsoup-1.18.3.jar` | Apenas `MANIFEST.MF`, `LICENSE`, `README.md` e o POM — nenhum `.properties`/`.json`/`.txt` de configuração |
| Reflection sobre classes da aplicação | Nenhuma no caminho usado (`Jsoup.parse` + `getElementsByClass`) |

Como o parser é alcançado por chamada direta e usa apenas as classes `Document`,
`Element` e `Elements` (todas estáticas em relação ao nosso código), não há
registro adicional a fazer. **Se** o binário acusar
`MissingReflectionRegistrationError` citando o JSoup, o caminho é rodar o Tracing
Agent — o procedimento está abaixo.

### Coletando metadata adicional automaticamente

Se aparecer `MissingReflectionRegistrationError` em alguma classe nova, o jeito
mais confiável é o **Tracing Agent**, que observa o programa rodando:

```powershell
# 1) Rode a aplicação com o agente (o .jar, nao o nativo)
java -agentlib:native-image-agent=config-output-dir=META-INF/native-image `
     -jar target/ECF-Notificador-1.0-SNAPSHOT.jar

# 2) Interaja com o programa (abra o menu, dispare uma verificacao)

# 3) Feche o programa - a metadata e gravada em:
#    META-INF/native-image/reachability-metadata.json

# 4) Gere o binario usando a metadata coletada
mvn -Pnative -DskipTests package
```

> A metadata vai para `src/main/resources/META-INF/native-image/` para entrar no classpath automaticamente.

---

## 🧪 Testes no binário nativo

Além do binário da aplicação, o projeto tem um perfil para compilar e executar a
**suíte de testes** como imagem nativa:

```bash
mvn -Pnative-test test
```

### Escopo — e honestidade sobre o que isso cobre

Só entram na imagem nativa os testes que fazem sentido no modelo *closed-world*:

| Grupo | Entra na imagem nativa? | Por quê |
|---|---|---|
| Regras puras (`TextSanitizer`, `VersionComparator`, `EcfPageParser`/JSoup, `UpdateHistoryStore`, `SettingsService`) | ✅ Sim | Sem servidor nem interface gráfica |
| `EcfLatestVersionServiceHttpTest` (WireMock) | ❌ Não (`@Tag("servidor")`) | Sobe um servidor HTTP embarcado; servidor dinâmico não cabe no closed-world |
| Testes de bandeja/janelas | ❌ Não (`@Tag("ui")`) | Precisam de bandeja do sistema e janelas reais |

> ⚠️ **O que o perfil `native-test` NÃO faz:** ele não prova que a aplicação
> inteira roda nativamente. Ele valida a camada de regra — que é onde os riscos
> de reflection e de recursos se concentram (Jackson, JSoup, enum lido por nome).
> O fluxo com servidor HTTP continua validado na JVM, onde o WireMock roda.

### Rodando localmente

1. Instale o GraalVM 25 (seção acima) e confirme com `native-image --version`.
2. `mvn -Pnative-test test`
3. O relatório fica em `target/surefire-reports/`, no mesmo formato da execução normal.

### Limitação que permanece

A bandeja (`SystemTray`) e a tela de Configurações (`JDialog`) continuam fora do
alcance do binário nativo, pelo motivo já explicado no início deste documento:
`java.desktop` não tem suporte oficial no Native Image. Nenhuma configuração de
metadata resolve isso.

---

## 🔍 Diagnóstico de falhas

Se o binário compilar mas quebrar em execução, quase sempre é metadata faltando. Use:

```bash
# Relata toda registration ausente como aviso, sem abortar
.\target\ECF-Notificador.exe -XX:MissingRegistrationReportingMode=Warn
```

Erros comuns e o que significam:

| Erro | Causa provável |
|---|---|
| `MissingReflectionRegistrationError` | Jackson tentou instanciar classe sem metadata |
| `ClassNotFoundException` em runtime | Classe acessada por nome não entrou no binário |
| `HeadlessException` / `NullPointerException` em AWT | `java.desktop` não suportado (limitação conhecida) |
| `Unsupported Feature` citando opções `-H:` | Opção experimental usada sem `-H:+UnlockExperimentalVMOptions` |

---

## 🧭 Caminhos possíveis, daqui pra frente

Como o `java.desktop` é a barreira, há três estratégias. Escolha conforme a prioridade do produto:

### Opção A — Manter o JAR como distribuição principal (recomendado hoje)

O programa continua distribuído como fat JAR (`java -jar`). O binário nativo é gerado "quando der" e usado apenas em cenários sem interface. **Custo: zero.** É o que o pipeline faz hoje.

### Opção B — Bandeja e tela de configuração via Win32 em vez de Swing

Reescrever a janela de Configurações e a bandeja usando **JNA + Win32** (no mesmo estilo do `TaskDialogService` já existente): `Shell_NotifyIcon` para a bandeja e `TaskDialog`/`CreateWindow` para as configurações, removendo o Swing do caminho crítico.

Isso tornaria o binário nativo **totalmente funcional**, pois elimina o `java.desktop`.

**Impacto:** refatoração média. A base já está pronta — `TaskDialogService` e `WinApiService` mostram exatamente o padrão de interop Win32 a seguir.

### Opção C — Runtime alternativo (Spring Boot Native / Quarkus)

Não se aplica: o problema não é framework, é `java.desktop`. Trocar de framework não resolve.

---

## ✅ Checklist para habilitar o binário de verdade

- [ ] Instalar **GraalVM for JDK 25** localmente e conferir `java -version` e `native-image --version`
- [ ] Gerar o binário com `mvn -Pnative -DskipTests package`
- [ ] Testar o `.exe` numa máquina **sem Java**
- [ ] Se faltar registro de reflexão, rodar o Tracing Agent e commitar a metadata adicional
- [ ] Decidir entre Opção A, B ou C acima
- [ ] Se optar por A: manter `continue-on-error: true` (estado atual)
- [ ] Se optar por B: remover `continue-on-error: true` após validar

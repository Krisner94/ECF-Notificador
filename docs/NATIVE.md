# 🧬 Compilação Nativa (GraalVM Native Image)

Este documento explica como gerar o **executável nativo** (`.exe` sem JVM) do ECF-Notificador, e — mais importante — **as limitações que você precisa conhecer antes de usar**.

O alvo do projeto é **Java 25**, e o binário nativo também é gerado para Java 25. Isso significa que **tanto a compilação do `.jar` quanto a do `.exe` exigem JDK 25**; o `.exe` exige adicionalmente que esse JDK seja o **GraalVM**.

---

## ✅ Estado da interface gráfica no binário nativo

O `java.desktop` (AWT/Swing) **não tem suporte no Native Image**, então toda a
interface do executável nativo foi reescrita em **Win32 via JNA** — o mesmo
padrão do `TaskDialogService`. O `App` escolhe a implementação em tempo de
execução: no binário nativo usa as classes `*.win32`, no JAR usa o Swing.

| Uso | No binário nativo | No JAR |
|---|---|---|
| Ícone na bandeja (`SystemTray`) | ✅ `Shell_NotifyIconW` (`NativeTrayService`) | `TrayIconService` (AWT) |
| Tela de Configurações (`JDialog`) | ✅ diálogo Win32 (`NativeConfigDialog`) | `ConfigForm` (Swing) |
| Popup de atualização | ✅ `TaskDialog` (JNA/Win32) | idem |
| Abrir a página de download | ✅ `ShellExecuteW` (JNA) | `java.awt.Desktop` |

O `java.desktop` continua **apenas** no caminho do JAR; no caminho nativo
nenhum componente AWT/Swing é acionado.

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

### O que ainda exige atenção

A UI nativa usa estruturas e callbacks JNA (`NOTIFYICONDATAW`, `MSG`,
`WNDCLASSEXW`, `WindowProc`). Essas classes estão registradas em
`reachability-metadata.json` para o Native Image enxergá-las via reflection. Se
novas estruturas/callbacks forem adicionados, é preciso atualizar esse arquivo —
ou rodar o Tracing Agent, como descrito acima.

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

## 🧭 Caminho adotado

A **Opção B** foi implementada: a bandeja e a tela de Configurações foram
reescritas em **JNA + Win32** (`NativeTrayService` via `Shell_NotifyIconW` e
`NativeConfigDialog` via `CreateWindowExW`), e o popup de atualização já usava
`TaskDialog`. O executável nativo fica **totalmente funcional** — bandeja, menu
de contexto, tela de Configurações e notificação — sem depender de `java.desktop`.

O fat JAR continua disponível como opção para quem prefere rodar com Java 25.

---

## ✅ Checklist de validacao do binario

- [x] Reescrita da bandeja em `Shell_NotifyIconW` (JNA)
- [x] Reescrita da tela de Configuracoes em `CreateWindowExW` (JNA)
- [x] Abertura da pagina de download via `ShellExecuteW` (sem `java.awt.Desktop`)
- [x] Metadata de reachability registrada para as novas estruturas/callbacks
- [ ] Gerar o binario com `mvn -Pnative -DskipTests package`
- [ ] Testar o `.exe` numa maquina **sem Java**: bandeja, menu, duplo clique,
      Configuracoes e popup de atualizacao

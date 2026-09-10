# 📋 Guia do Pipeline de CI/CD

Este documento explica **o que o pipeline faz, por que cada etapa existe e o que você precisa configurar** antes do primeiro merge para a master.

---

## 🎯 Visão Geral

| Necessidade | Ferramenta escolhida | Papel |
|---|---|---|
| Linguagem / alvo | **Java 25** | `maven.compiler.release=25`, exigido pelo Enforcer |
| Testes unitários | **JUnit 5 + Surefire** | Roda a suíte em todo PR e push |
| Cobertura de testes | **JaCoCo** | Mede cobertura e falha abaixo do mínimo |
| Qualidade de código (Sonar) | **Checkstyle + PMD + SpotBugs** | Estilo, bugs e code smells |
| **Proibir `var`** | **Checkstyle (RegexpSinglelineJava)** | Bloqueia `var` em qualquer PR |
| Segurança de dependências (Veracode SCA) | **OWASP Dependency-Check** | CVEs + grau de risco (CVSS) |
| Inventário de dependências (VEX) | **CycloneDX** | Gera SBOM (JSON/XML) |
| Revisão de dependências no PR | **Dependency Review + Dependabot** | Bloqueia CVE nova antes do merge |
| Segurança de código (Veracode SAST) | **CodeQL + Semgrep** | Fluxo de dados e regras OWASP |
| Binário distribuível | **GraalVM Native Image** | Gera `.exe` sem JVM (perfil `native`) |

> **Por que não o SonarQube/Veracode direto?** Ambos são produtos pagos/self-hosted e não rodam nativamente no GitHub Actions. As ferramentas acima cobrem **a mesma finalidade** de forma gratuita. A seção [Como plugar o SonarQube real](#-como-plugar-o-sonarqube-real) mostra como trocar depois.

---

## 📁 Arquivos criados

```
.github/
├── workflows/
│   ├── ci.yml                  # PR + push: testes, qualidade, segurança, build
│   ├── master.yml              # Pós-merge na master: verify completo + SBOM + release
│   └── dependency-review.yml   # PR: bloqueia dependência vulnerável
└── dependabot.yml              # Abre PRs de atualização semanal

config/
├── checkstyle/checkstyle.xml       # Regras de estilo + PROIBIDO VAR
├── pmd/pmd-ruleset.xml             # Análise estática (Sonar-like)
└── spotbugs/spotbugs-exclude.xml   # Supressões justificadas + FindSecBugs

scripts/
└── build-local.ps1                 # Build + testes sem Maven (uso local)

src/main/resources/META-INF/native-image/...   # Metadata do GraalVM
src/test/java/...                    # Testes unitários e parametrizados
```

---

## ⚙️ Configuração necessária no GitHub (5 minutos)

### 1. Habilite o Dependency Graph
`Settings → Security → Dependency graph` → **Enable**

Sem isso o job `dependency-review` não funciona.

### 2. Habilite o Code Scanning (CodeQL)
`Settings → Security → Code scanning` → habilitar. Os alertas aparecem em **Security → Code scanning alerts**.

### 3. Adicione o secret `NVD_API_KEY` (recomendado)
O OWASP Dependency-Check consulta a base NVD. Sem chave, o download é **muito lento** e pode ser limitado.

1. Solicite uma chave gratuita em <https://nvd.nist.gov/developers/request-an-api-key>
2. `Settings → Secrets and variables → Actions → New repository secret`
3. Nome: `NVD_API_KEY` — Valor: sua chave

### 4. (Opcional) Torne o check obrigatório no PR
`Settings → Branches → Add branch protection rule` → marque **Require status checks to pass** e selecione:
- `Testes unitarios`
- `Analise estatica (Sonar-like)`
- `SAST (Veracode-like)`

---

## ☕ Versão do Java

O projeto inteiro — build, testes e binário nativo — é **Java 25**:

```xml
<maven.compiler.release>25</maven.compiler.release>
```

O `maven-enforcer-plugin` **reprova o build** se o JDK for anterior ao 25:

```xml
<requireJavaVersion>
    <version>[25,)</version>
</requireJavaVersion>
```

Por isso todos os jobs usam `java-version: '25'`, e os jobs de binário nativo usam
`distribution: 'graalvm'` (o `native-image` só existe no GraalVM).

---

## 🧪 A suíte de testes

**499 testes, 100% passando.** A suíte está organizada por módulo de risco, com
testes parametrizados onde há muitas entradas equivalentes.

| Classe | O que garante |
|---|---|
| `TextSanitizerTest` | Sanitização do texto vindo da rede e validação de URL (só HTTP/HTTPS) |
| `EcfPageParserTest` | Parsing do HTML da Receita Federal com **JSoup** (elemento do DOM, não regex) |
| `EcfLatestVersionServiceHttpTest` | HTTP 200/404/500/503, timeout, queda de conexão e agendador resiliente (**WireMock**) |
| `VersionComparatorTest` | Comparação numérica (10 > 9), sufixos, rollback, **zero alocação** por chamada |
| `UpdateServiceTest` | **Quando** o usuário é avisado e **o que** aparece no popup |
| `UpdateHistoryStoreTest` | Persistência local, escrita atômica, concorrência e arquivo corrompido |
| `SettingsServiceTest` | Precedência Gist × escolha do usuário e persistência entre reinícios |
| `TrayIconServiceTest` | Modelo do menu e, principalmente, **a verificação nunca roda na EDT** |
| `UpdateSchedulerTest` (no `TrayIconServiceTest`) | Serialização das verificações e isolamento de falhas |
| `TaskDialogServiceTest` | Layout empacotado (`pshpack1`) da `TASKDIALOGCONFIG` e dos botões |
| `WinApiServiceTest` | Carregamento/liberação de HICON com duble de `user32` |
| `IconLoaderTest` / `IconLoaderDibTest` | PNG/BMP reais, container ICO, DIB 32/24/1 bpp, máscara AND |
| `EcfVersionServiceTest` | Parsing do `response.varfile` e tolerância a caminhos inválidos |
| `GistSettingsLoaderTest` | URL do Gist, JSON inválido e campos desconhecidos |
| `MessageTest` | Textos da interface preenchidos, sem perguntas e sem quebras de linha |
| `ThemeServiceTest` | Instalação do tema é idempotente e nunca derruba o app |

> Nenhum teste acessa a internet, o disco do usuário, ou abre janela. O `pom.xml`
> força `java.awt.headless=true`, o `HttpClient` é injetável e os serviços nativos
> (JNA) aceitam um duble. O popup nativo (`TaskDialogIndirect`) é **modal** e, por
> isso, jamais é exibido pela suíte — ele travaria a execução.

### Cobertura (medida)

| Métrica | Antes | Agora |
|---|---|---|
| Linhas | 49,1% | **75,0%** |
| Ramos | 51,4% | **75,8%** |
| Instruções | 44,2% | **76,3%** |

O piso no `pom.xml` subiu de `0.50` para `0.70`, com comentário justificando o
valor real medido.

### Grupos de teste

| Tag | Efeito |
|---|---|
| `ui` | Excluída da execução padrão e do perfil nativo (precisa de bandeja/janelas) |
| `servidor` | Excluída do perfil nativo (WireMock sobe servidor HTTP) |

### Rodando só um grupo

```bash
# Toda a suíte (exceto testes marcados com @Tag("ui"))
mvn test

# Uma classe
mvn test -Dtest=VersionComparatorTest

# Um método específico
mvn test -Dtest=UpdateServiceTest#versaoInstaladaAusente

# Testes que precisam de ambiente gráfico
mvn test -Dgroups=ui

# Testes no binário nativo (precisa de GraalVM 25)
mvn -Pnative-test test
```


---

## 🔒 A regra que proíbe `var`

```xml
<module name="RegexpSinglelineJava">
    <property name="format"
              value="(^\s*|[({;,]\s*)var\s+[A-Za-z_$][\w$]*\s*[=;:,)]"/>
    <property name="message"
              value="Uso de 'var' nao permitido. Declare o tipo explicitamente."/>
</module>
```

### O detalhe que importa
O regex exige que `var` esteja **em posição de tipo** (início da linha ou após `(`, `{`, `;`, `,`).

Isso é essencial: um padrão ingênuo como `\bvar\s+` casaria **dentro de strings** — por exemplo:

```java
String texto = "usar var aqui";   // ❌ falharia o build indevidamente
```

Resultado validado:

| Cenário | Resultado |
|---|---|
| `var x = 10;` | ❌ reprovado |
| `for (var item : itens)` | ❌ reprovado |
| `try (var s = ...)` | ❌ reprovado |
| `String variavel = "ok";` | ✅ aprovado |
| `String t = "usar var aqui";` | ✅ aprovado |

**9/9 casos de `var` reprovados · 13/13 casos legítimos aprovados.**

---

## 🧪 Cobertura de testes

O JaCoCo está configurado para **falhar abaixo de 70%** de instruções:

```xml
<coverageMinimum>0.70</coverageMinimum>
```

O valor foi elevado de `0.50` depois que a suíte passou a cobrir os módulos de
rede, persistência e layout nativo. A cobertura medida é **76,3% de instruções**,
então o piso dá folga para refatorações sem deixar de proteger.

> **Como evoluir:** só suba o piso quando a medição real estiver alguns pontos
> acima dele. Um limite colado no valor atual transforma qualquer refatoração em
> build vermelho e o portão perde credibilidade.

Para ver o relatório local: `target/site/jacoco/index.html`

### Medindo sem Maven

Quando o `mvn` não está disponível, os scripts do repositório fazem a medição:

```powershell
# Compila, roda a suite sob o agente e gera o HTML/CSV
cmd /c scripts\cobertura.cmd

# Le o jacoco.csv e imprime o resumo por classe
powershell -ExecutionPolicy Bypass -File scripts\relatorio-cobertura.ps1
```

Saída: `target\jacoco-report\index.html` (navegável) e `target\cobertura.txt`
(resumo textual com os totais e a cobertura por classe).

---

## 🛡️ Grau de risco das dependências

O OWASP Dependency-Check **falha o build** quando encontra vulnerabilidade com **CVSS ≥ 7** (severidade alta):

```xml
<cvssFailThreshold>7</cvssFailThreshold>
```

Ajuste em `pom.xml` conforme sua política:

| Valor | Significado |
|---|---|
| `9` | Só falha em críticas |
| `7` | Falha em altas e críticas *(padrão atual)* |
| `4` | Falha em médias, altas e críticas |

A pipeline gera relatórios em **HTML, JSON e SARIF** (SARIF aparece na aba *Security* do GitHub).

---

## 🚀 Como rodar localmente

```bash
# Testes
mvn test

# Testes + cobertura + qualidade (mesmo que a pipeline)
mvn verify

# Somente a regra de estilo (inclui proibido-var)
mvn checkstyle:check

# Segurança de dependências (baixa a base NVD — primeira vez é demorado)
mvn verify -Psecurity

# Gerar o SBOM
mvn cyclonedx:makeAggregateBom

# Gerar o executavel nativo (precisa de GraalVM 25, nao de Temurin)
mvn -Pnative -DskipTests package
```

> **Nota:** neste ambiente o `mvn` pode não estar no `PATH`. Sem Maven, use os
> scripts do repositório (a fonte de verdade continua sendo o `mvn verify`):
>
> ```powershell
> scripts\validar-tudo.cmd      # compila main+test e roda a suite
> scripts\run-tests.cmd         # so a suite (usa o que ja esta compilado)
> scripts\cobertura.cmd         # suite + relatorio JaCoCo
> ```

---

## 🔌 Como plugar o SonarQube real

Se sua organização já usa SonarQube/SonarCloud, adicione ao `pom.xml`:

```xml
<plugin>
    <groupId>org.sonarsource.scanner.maven</groupId>
    <artifactId>sonar-maven-plugin</artifactId>
    <version>5.8.0.7211</version>
</plugin>
```

E no workflow, substitua o job `analise-estatica`:

```yaml
- name: SonarQube
  env:
    SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
    SONAR_HOST_URL: ${{ secrets.SONAR_HOST_URL }}
  run: mvn -B verify sonar:sonar
```

Secrets necessários: `SONAR_TOKEN` e `SONAR_HOST_URL`.

> **Vantagem de manter os dois:** o Checkstyle continua sendo o "portão rápido" no PR (segundos), enquanto o Sonar faz a análise profunda.

---

## 🔌 Como plugar o Veracode real

O Veracode tem action oficial. Adicione ao `ci.yml`:

```yaml
veracode:
  name: Veracode (SAST)
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-java@v4
      with:
        distribution: temurin
        java-version: '25'
    - name: Empacotar
      run: mvn -B package -DskipTests
    - name: Upload e Scan
      uses: veracode/veracode-uploadandscan-action@0.2.6
      with:
        appname: 'ECF-Notificador'
        createprofile: true
        filepath: 'target/ECF-Notificador-1.0-SNAPSHOT.jar'
        vid: ${{ secrets.VERACODE_API_ID }}
        vkey: ${{ secrets.VERACODE_API_KEY }}
```

Secrets: `VERACODE_API_ID` e `VERACODE_API_KEY`. Isso requer conta Veracode paga.

---

## ✅ Checklist antes do primeiro merge na master

- [ ] Confirmar que o CI usa **JDK 25** (e **GraalVM 25** nos jobs nativos)
- [ ] Habilitar **Dependency graph**
- [ ] Habilitar **Code scanning** (CodeQL)
- [ ] Criar secret **`NVD_API_KEY`**
- [ ] (Opcional) Proteger a branch exigindo os checks
- [ ] Rodar `mvn verify` localmente e confirmar que passa
- [ ] Revisar as supressões em `config/spotbugs/spotbugs-exclude.xml` — cada uma deve ter justificativa
- [ ] Revisar o `coverageMinimum` (hoje `0.70`, com medição real de 76,3%)

---

## 📊 Onde ver os resultados

| O que | Onde |
|---|---|
| Logs e falhas | Aba **Actions** |
| Cobertura e qualidade | Artifacts `relatorios-qualidade` |
| CVEs e SBOM | Artifacts `seguranca-dependencias` / `sbom-e-seguranca` |
| Binário nativo (`.exe`) | Artifacts `ecf-notificador-nativo` / `ecf-notificador-native-release` |
| Alertas de segurança do código | Aba **Security → Code scanning** |
| Dependências vulneráveis | Aba **Security → Dependabot** |
| Comentário no PR | Automático (Dependency Review) |

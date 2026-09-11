# ⚙️ Configuração via Gist

O ECF-Notificador lê a configuração de um **Gist público do GitHub**, que funciona como fonte de verdade compartilhada entre todas as instalações.

> **Requisito de execução:** Java 25 (no `.jar`) ou nenhum runtime (no `.exe`
> nativo). Veja o `README.md` e o `docs/NATIVE.md`.

---

## 🔗 Gist usado

| | |
|---|---|
| **Página** | <https://gist.github.com/Krisner94/e2e1820cb36f7bee41a5212fe9dbad35> |
| **URL raw (usada pelo programa)** | `https://gist.githubusercontent.com/Krisner94/e2e1820cb36f7bee41a5212fe9dbad35/raw/appSettings.json` |

> O programa usa a URL **`raw`** (conteúdo puro), não a página HTML do Gist.

### Formato esperado

```json
{
  "Ecf": {
    "InstallPath": "C:\\Arquivos de Programas RFB\\Programas SPED\\SpedECF\\.install4j\\response.varfile",
    "DownloadUrl": "https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/download/sped/ecf",
    "VersionHtmlClass": "rfb_subheader"
  },
  "Settings": {
    "CheckIntervalHours": 6,
    "IconPath": "EcfNotificador.ico"
  }
}
```

---

## 🔄 Como o programa carrega a configuração

> **O Gist é a única fonte de configuração.** Não existe arquivo de configuração
> versionado no projeto. O `config/appSettings.json` é um **cache** que o próprio
> programa cria e regrava sozinho — ele está no `.gitignore`.

```
Inicialização
     │
     ├─ 1. Lê o cache local  (config/appSettings.json, se já existir)
     │       └─ Só existe depois da primeira execução bem-sucedida
     │
     ├─ 2. Baixa o Gist      (GistSettingsLoader)
     │       │
     │       ├─ Sucesso ──► aplica sobre o cache + salva o cache
     │       │
     │       └─ Falha   ──► MANTÉM o cache local (programa segue offline)
     │
     └─ 3. A cada 5 min, repete o passo 2  (sem reiniciar o programa)
```

## ⚡ Propagação da mudança (o ponto central)

O objetivo do projeto é **nunca precisar de uma nova release** para corrigir uma
URL quebrada, uma classe HTML que mudou ou o caminho da versão instalada (que
mudam sem aviso). Por isso a configuração é relida automaticamente.

| Mecanismo | Quando |
|---|---|
| **Recarga automática** | A cada **5 minutos**, em segundo plano |
| **Menu → Verificar agora** / duplo clique | Relê a config **antes** de checar a versão |
| **Reiniciar o app** | Sempre relê |

> **Por que 5 minutos, e não menos?** A URL `raw` do Gist é servida pelo CDN do
> GitHub com `Cache-Control: max-age=300`. Verificar mais rápido não anteciparia a
> mudança — o CDN ainda entregaria o conteúdo antigo.

### O custo de verificar é quase zero

A recarga usa `ETag` / `If-None-Match`. Se nada mudou, o Gist responde **HTTP 304
sem corpo**. Medido no Gist de produção:

| Situação | Tempo | Tráfego |
|---|---|---|
| Gist mudou (`200`) | ~620 ms | JSON completo |
| Gist inalterado (`304`) | **~14 ms** | nenhum corpo |

### Comportamento em cada cenário

| Cenário | O que acontece |
|---|---|
| **Gist acessível** | Valores do Gist prevalecem e são gravados no cache local |
| **Sem internet / Gist fora** | Usa o último valor em cache — **o programa não deixa de iniciar** |
| **Primeira execução sem internet** | Sem cache e sem Gist, vale o padrão da classe (ver abaixo) |
| **JSON inválido no Gist** | Ignora o conteúdo remoto e mantém o cache local |
| **JSON parcial / com chave em branco** | Só as chaves presentes são aplicadas — **nada é apagado** |
| **Campo novo no Gist** | Ignorado (`FAIL_ON_UNKNOWN_PROPERTIES = false`) — não quebra versões antigas |
| **Gist alterado com o programa aberto** | Aplicado na próxima recarga (até 5 min) ou na hora pelo "Verificar agora" |

> 💡 **Princípio de design:** a rede nunca é motivo para o programa falhar. Toda falha de download degrada para o cache local.

> ⚠️ **Nunca edite o `config/appSettings.json` para mudar a configuração.**
> O arquivo é sobrescrito na próxima recarga do Gist. Para alterar qualquer
> valor, edite o **Gist**.

---

## 🧩 Precedência do intervalo de verificação

O `CheckIntervalHours` tem uma regra especial:

| Situação | Valor usado |
|---|---|
| Usuário **nunca** alterou pela tela de Configurações | **Gist** (fonte de verdade) |
| Usuário **já alterou** pela tela de Configurações | **Escolha do usuário** |

Sem isso, a alteração feita pelo usuário seria sobrescrita pelo Gist a cada reinício. O controle é a flag `intervalOverriddenByUser`.

### Onde a flag é gravada

A flag é salva **no cache local** (`config/appSettings.json`), na chave `IntervalOverriddenByUser`:

```json
{
  "Settings": {
    "CheckIntervalHours": 3,
    "IconPath": "EcfNotificador.ico",
    "IntervalOverriddenByUser": true
  }
}
```

> ⚠️ **Essa chave nunca vem do Gist e é sempre gravada pelo programa.** Ela fica
> apenas no cache local, porque representa uma escolha da máquina do usuário —
> não um valor compartilhado. Se ela desaparecer (cache apagado), o programa
> volta a tratar o Gist como fonte de verdade e o intervalo escolhido na tela de
> Configurações é perdido. O que vem do Gist é aplicado campo a campo
> (`CheckIntervalHours` e `IconPath`), então a marca nunca é sobrescrita por
> engano — apenas o `setCheckIntervalHours(...)` a define.

### Valores usados quando não há Gist nem cache

Se o Gist estiver inacessível **e** não existir cache local (primeira execução
sem internet), o programa usa os padrões abaixo. Eles ficam em
`app.service.SettingsService`, **não** nos campos das classes de configuração:
os campos começam nulos de propósito, para que "ausente no JSON" seja
distinguível de "veio vazio" e o merge saiba quando preservar o valor atual.

| Campo | Padrão |
|---|---|
| `Ecf.InstallPath` | `""` (vazio — a verificação resulta em `UNKNOWN`) |
| `Ecf.DownloadUrl` | `""` |
| `Ecf.VersionHtmlClass` | `rfb_subheader` |
| `Settings.CheckIntervalHours` | `6` |
| `Settings.IconPath` | `EcfNotificador.ico` |

### 🔒 Edição parcial não quebra as instalações

O merge é **campo a campo**: somente as chaves presentes no JSON do Gist
sobrescrevem o valor atual. Uma chave ausente, `null` ou em branco **preserva** o
que já existia.

Isso protege contra o erro mais provável ao editar o Gist às pressas — esquecer
um bloco ou deixar um valor vazio:

```json
{ "Settings": { "CheckIntervalHours": 12 } }
```

Com esse JSON, o `DownloadUrl` e o `InstallPath` **continuam valendo** (vêm do
cache) e apenas o intervalo é atualizado. Sem essa proteção, um único campo
esquecido inutilizaria todas as instalações em até 5 minutos.

---

## 🧪 Testando

### Apontar para outro Gist sem recompilar

Útil para testar um Gist alternativo:

```bash
java -Dapp.config.gistUrl="https://gist.githubusercontent.com/SEU_USUARIO/SEU_ID/raw/appSettings.json" \
     -jar target/ECF-Notificador-1.0-SNAPSHOT.jar
```

### Verificar se o Gist está acessível

```powershell
Invoke-WebRequest -Uri "https://gist.githubusercontent.com/Krisner94/e2e1820cb36f7bee41a5212fe9dbad35/raw/appSettings.json" `
  -UseBasicParsing | Select-Object StatusCode, Content
```

`StatusCode` deve ser `200`.

### Executar com outra configuração sem recompilar

Além da URL do Gist (`-Dapp.config.gistUrl`), o cache local é relativo ao
**diretório de execução**: o programa grava em `config/appSettings.json` ao lado
de onde foi iniciado. Para testar em uma pasta separada, rode o JAR a partir dela.

### Testes automatizados

`src/test/java/app/service/GistSettingsLoaderTest.java` cobre:

- URL aponta para o Gist correto (formato `raw` + `appSettings.json`)
- URL inválida, host inexistente e erro de rede → `null` (sem exceção)
- Desserialização preserva todos os valores do JSON real
- Campo desconhecido é ignorado
- Chave ausente fica `null` (para o merge preservar o valor atual)
- JSON inválido, incompleto ou vazio → `null`, sem exceção

`src/test/java/app/service/GistSettingsLoaderHttpTest.java` (tag `servidor`) cobre o
download de verdade contra um servidor embarcado:

- HTTP 200 entrega a configuração; erro 3xx/4xx/5xx → `null`
- Mudança publicada no Gist é vista na recarga seguinte
- `ETag` é guardado e reenviado em `If-None-Match`; `304` → `null` sem erro
- `304` preserva o `ETag` anterior
- Queda de conexão → `null`, sem derrubar o programa

`src/test/java/app/service/SettingsServiceTest.java` cobre a precedência e a persistência:

- Gist define o intervalo enquanto o usuário não mexer
- Escolha do usuário prevalece sobre o Gist
- A escolha **sobrevive ao reinício** do programa
- Gist continua atualizando campos que o usuário não controla
- Valores inválidos (intervalo ≤ 0, ícone em branco, classe HTML em branco) caem no padrão
- **Gist parcial não apaga a configuração** (bloco ausente, bloco vazio, valores em branco)
- Mudança de URL, classe HTML e caminho é aplicada imediatamente
- O cache em disco nunca guarda valores apagados
- `refreshFromGist()` só sinaliza mudança quando algo realmente mudou

> Os testes unitários **não acessam a internet**. Os testes de HTTP real usam
> WireMock local e ficam fora do perfil `native-test` pela tag `servidor`.

### Validar uma edição do Gist na hora

1. Edite o Gist e clique em **Update public gist**.
2. Espere o CDN expirar (**até 5 min**) ou, para não esperar, clique com o botão
   direito no ícone da bandeja → **Verificar agora** (ele relê a config antes de
   checar a versão).
3. Confira o resultado em `config/appSettings.json`.

Para acompanhar pelo log, suba o nível para `debug`:

```powershell
java "-Dorg.slf4j.simpleLogger.defaultLogLevel=debug" -jar target\ECF-Notificador-1.0-SNAPSHOT.jar
```

Mensagens úteis: `Configuracao carregada do Gist com sucesso.`,
`Gist inalterado (304).` e `Configuracao atualizada pelo Gist: ...`.

---

## ⚠️ Escolha do Gist: público vs. secreto

O Gist atual está marcado como **"Secret"** no GitHub.

**Isso NÃO o torna privado.** Gists "secret" apenas não aparecem em listagens públicas — mas **qualquer pessoa com a URL lê o conteúdo**, e a URL `raw` responde sem autenticação (confirmei: HTTP 200 sem token).

Para esta configuração isso é aceitável, porque o conteúdo é apenas:
- caminho de instalação padrão da ECF
- URL pública da Receita Federal
- classe HTML e intervalo de verificação

**Nenhum segredo, token ou dado pessoal.** Mas se um dia precisar colocar algo sensível aqui, use um repositório privado com token de acesso — um Gist não protege nada.

---

## 🛠️ Atualizando a configuração

1. Abra a página do Gist
2. Clique em **Edit**
3. Altere o JSON
4. **Update public gist**

As instalações já em execução aplicam a mudança na próxima recarga (até 5 min).
Para forçar na hora, use **Verificar agora** no menu da bandeja.

> Você pode alterar **só o campo que precisa**. Chaves omitidas continuam com o
> valor que já estava funcionando — não é preciso republicar o JSON inteiro.

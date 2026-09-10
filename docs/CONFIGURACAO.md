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

```
Inicialização
     │
     ├─ 1. Lê o cache local  (config/appSettings.json, se existir)
     │
     ├─ 2. Baixa o Gist      (GistSettingsLoader)
     │       │
     │       ├─ Sucesso ──► aplica sobre o cache + salva o cache
     │       │
     │       └─ Falha   ──► MANTÉM o cache local (programa segue offline)
     │
     └─ 3. A cada 6 h, repete o passo 2  (sem precisar reiniciar)
```

### Comportamento em cada cenário

| Cenário | O que acontece |
|---|---|
| **Gist acessível** | Valores do Gist prevalecem e são gravados no cache local |
| **Sem internet / Gist fora** | Usa o último valor em cache — **o programa não deixa de iniciar** |
| **JSON inválido no Gist** | Ignora o conteúdo remoto e mantém o cache local |
| **Campo novo no Gist** | Ignorado (`FAIL_ON_UNKNOWN_PROPERTIES = false`) — não quebra versões antigas |
| **Gist alterado com o programa aberto** | Novo valor aplicado na próxima recarga (a cada 6 h) |

> 💡 **Princípio de design:** a rede nunca é motivo para o programa falhar. Toda falha de download degrada para o cache local.

---

## 🧩 Precedência do intervalo de verificação

O `CheckIntervalHours` tem uma regra especial:

| Situação | Valor usado |
|---|---|
| Usuário **nunca** alterou pela tela de Configurações | **Gist** (fonte de verdade) |
| Usuário **já alterou** pela tela de Configurações | **Escolha do usuário** |

Sem isso, a alteração feita pelo usuário seria sobrescrita pelo Gist a cada reinício. O controle é a flag `intervalOverriddenByUser`.

### Onde a flag é gravada

A flag é salva **no arquivo local** (`config/appSettings.json`), na chave `IntervalOverriddenByUser`:

```json
{
  "Settings": {
    "CheckIntervalHours": 3,
    "IconPath": "EcfNotificador.ico",
    "IntervalOverriddenByUser": true
  }
}
```

> ⚠️ **Não remova essa chave do arquivo local.** Se ela desaparecer, o programa
> volta a tratar o Gist como fonte de verdade e o intervalo escolhido pelo usuário
> é perdido no próximo reinício. Isso é intencional: o que vem do Gist é aplicado
> campo a campo (`CheckIntervalHours` e `IconPath`), então a marca nunca é
> sobrescrita por engano — apenas o `setCheckIntervalHours(...)` a define.

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

Além da URL do Gist (`-Dapp.config.gistUrl`), o arquivo local de configuração é
relativo ao diretório de execução. Para testar em uma pasta separada, rode o JAR
a partir dela.

### Testes automatizados

`src/test/java/app/service/GistSettingsLoaderTest.java` cobre:

- URL aponta para o Gist correto (formato `raw` + `appSettings.json`)
- URL inválida, host inexistente e erro de rede → `null` (sem exceção)
- Desserialização preserva todos os valores do JSON real
- Campo desconhecido é ignorado
- JSON inválido, incompleto ou vazio → `null`, sem exceção

`src/test/java/app/service/SettingsServiceTest.java` cobre a precedência e a persistência:

- Gist define o intervalo enquanto o usuário não mexer
- Escolha do usuário prevalece sobre o Gist
- A escolha **sobrevive ao reinício** do programa
- Gist continua atualizando campos que o usuário não controla
- Valores inválidos (intervalo ≤ 0, ícone em branco, classe HTML em branco) caem no padrão

> Os testes **não acessam a rede**: validam o comportamento tolerante a falha, para não deixar o CI lento ou instável.

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

As instalações já em execução aplicam a mudança na próxima recarga (até 6 h). Para forçar, reabra o programa.

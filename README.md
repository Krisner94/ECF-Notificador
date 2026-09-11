# 🚀 ECF-Notificador - Guia do Usuário

O **ECF-Notificador** é um assistente digital que monitora automaticamente o site da Receita Federal para você. Ele verifica se existe uma nova versão do programa **ECF (Escrituração Contábil Fiscal)** disponível, evitando que você precise checar o site manualmente todos os dias.

---

## 📦 1. Como Obter o Programa

### **Pré-requisitos**

| Para... | Você precisa de |
|---|---|
| **Só usar o `.exe`** | Nada além do **Windows 10/11** — o executável nativo não usa Java |
| **Rodar o `.jar`** | **Java 25** (JRE ou JDK) |
| **Compilar o projeto** | **JDK 25** + **Maven 3.8+** |
| **Gerar o `.exe`** | **GraalVM 25** (não serve OpenJDK/Temurin comum) — ver `docs/NATIVE.md` |

### **Opção A — Executável nativo (recomendado)**

O `.exe` gerado com GraalVM **não precisa de Java instalado**:

```powershell
.\ECF-Notificador.exe
```

> ⚠️ Nesta versão o `.exe` exibe o **popup de atualização**, mas ainda **não** tem
> o ícone na bandeja nem a tela de Configurações — esses dois usam AWT/Swing, que
> o Native Image não suporta. Veja `docs/NATIVE.md` para o detalhamento.

### **Opção B — fat JAR**

Com Maven e o JDK 25 instalados, gere o pacote na raiz do projeto:

```bash
mvn clean package
```

O arquivo `ECF-Notificador-1.0-SNAPSHOT.jar` (fat JAR com todas as dependências) será gerado na pasta `target/`.

### **Execução**

```bash
java -jar target/ECF-Notificador-1.0-SNAPSHOT.jar
```

O programa roda discretamente na bandeja do sistema (ao lado do relógio do Windows).

### **Desenvolvimento: build e testes locais**

Com Maven instalado:

```bash
mvn verify        # compila, testa, mede cobertura e roda a análise estática
```

Se o `mvn` não estiver no `PATH`, o repositório traz scripts equivalentes que usam
o JDK 25 e o repositório `.m2` diretamente:

```powershell
# Compila (release 25) e roda a suíte completa
scripts\validar-tudo.cmd          # resumo em target\resumo.txt

# Só uma classe de teste
scripts\run-tests.cmd app.utils.VersionComparatorTest

# Cobertura (gera target\jacoco-report\index.html)
scripts\cobertura.cmd
powershell -ExecutionPolicy Bypass -File scripts\relatorio-cobertura.ps1
```

> Os scripts usam `C:\Users\Rhama\.jdks\temurin-25.0.4.1` por padrão; defina
> `ECF_JDK` (e opcionalmente `ECF_M2`) para apontar outro JDK/repositório.

---

## ⚙️ 2. Configuração Inicial

**Não há arquivo de configuração para editar.** O programa busca tudo de um **Gist público do GitHub**, que é a fonte de verdade compartilhada entre todas as instalações.

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

Na primeira execução o programa baixa esse JSON e grava uma cópia em `config/appSettings.json`,
que serve **apenas como cache** para funcionar offline. Esse arquivo é regenerado
sozinho a cada recarga e **não é versionado no git** — editá-lo não adianta, pois o
Gist sobrescreve os valores na próxima atualização.

> ⚡ **Mudou algo no Gist?** A aplicação pega a alteração **em até 5 minutos**, sem
> reinstalar nem reiniciar. Para aplicar na hora, use **Verificar agora** no menu
> da bandeja. Só é preciso atualizar os campos que mudaram: chaves omitidas
> continuam valendo, então uma edição parcial nunca quebra as instalações.

> 💡 **Ícone:** a chave `IconPath` aceita arquivos `.ico`, `.png`, `.bmp`, `.gif` e `.jpg`. Se o valor não for um caminho absoluto, o programa procura o arquivo na pasta onde o JAR é executado. O mesmo ícone é usado na bandeja **e** no popup de notificação.

> 📖 Detalhes de precedência, teste e diagnóstico em [`docs/CONFIGURACAO.md`](docs/CONFIGURACAO.md).

---

## 🖥️ 3. Como Usar o Programa

### **Primeira Execução**

1. **Abrir o programa:** execute `java -jar ECF-Notificador-1.0-SNAPSHOT.jar`.
2. **Ícone na Bandeja:** o programa roda discretamente ao lado do relógio do Windows.
3. **Configuração inicial:** verifique se o caminho da ECF está correto nas configurações.

### **Menu de Opções**

Clique com o **botão direito** no ícone na bandeja do sistema para acessar:

- **📡 Verificar agora:** faz uma busca imediata por atualizações no site da Receita Federal.
- **⚙️ Configurações:** define o intervalo de verificação automática (ex: a cada 6 horas).
- **❌ Sair:** fecha o programa totalmente.

> 💡 **Duplo clique** no ícone da bandeja faz o mesmo que **Verificar agora**.

### **Funcionamento Automático**

- O programa verifica atualizações automaticamente no intervalo configurado.
- Opera em segundo plano com consumo mínimo de recursos.
- O menu **Verificar agora**, o duplo clique no ícone e a verificação automática exibem **exatamente o mesmo popup**.

### **Aparência da Interface**

As janelas usam o **FlatLaf** seguindo o **tema padrão do Windows**:

- **🪟 Mesmo tema do popup:** as cores são lidas direto do Windows (`GetSysColor`), então a janela de Configurações fica visualmente **idêntica** ao popup de atualização.
- **🔤 Fonte nativa:** usa a mesma fonte da interface do sistema (Segoe UI).
- **🪟 Decoração nativa:** barras de título do próprio Windows, como em qualquer aplicativo do sistema.

> ℹ️ As cores acompanham o padrão do Windows automaticamente — nenhum ajuste manual é necessário.

---

## 🔔 4. Entendendo o Aviso de Atualização

Quando uma nova versão da ECF for detectada, o programa exibe um **popup nativo do Windows** (TaskDialog) com o ícone do programa:

```
Nova versão 12.2.6 disponível do ECF

Versão atual: 12.2.5

[ Baixar agora ]  [ Adiar ]
```

### **Características do Aviso:**

- **🪟 Popup Nativo:** é a mesma janela usada por aplicativos do sistema, com o ícone do programa.
- **✅ Botão "Baixar agora":** abre o navegador direto na página oficial de downloads da Receita Federal.
- **⏰ Botão "Adiar":** fecha o aviso e mantém o programa monitorando em segundo plano.
- **� X desabilitado:** o botão de fechar da janela fica **inativo** (assim como `Esc` e `Alt+F4`), garantindo que você escolha uma das duas opções.
- **📄 Seção "Detalhes":** mostra o link oficial de download.

> ℹ️ O aviso é **apenas informativo**: nenhuma pergunta é feita ao usuário. Basta escolher entre baixar agora ou adiar.

### **O que acontece quando há atualização:**

1. O programa detecta uma nova versão no site da Receita Federal.
2. Compara com a versão instalada no seu computador.
3. Se for diferente, exibe o popup com a nova versão e a versão atual.
4. Escolha **Baixar agora** para acessar o download oficial ou **Adiar** para ser lembrado depois.

> ⚠️ **Nota:** se as notificações do Windows estiverem desativadas, o balão da bandeja é descartado silenciosamente pelo sistema. Por isso o programa usa o popup nativo, que funciona independentemente dessa configuração.

---

## 🔄 5. Iniciando Automaticamente com o Windows

**Com o executável nativo:** crie um atalho para o `ECF-Notificador.exe`.

**Com o JAR:** crie um atalho para o JAR (ou um `.bat` que execute o `java -jar`).

Depois, em ambos os casos:

1. Pressione **Windows + R**, digite `shell:startup` e pressione **Enter**.
2. Cole o **atalho** dentro desta pasta.

---

## 🗑️ 6. Como Desinstalar

1. Feche o programa (botão direito no ícone > Sair).
2. Exclua a pasta onde o programa está instalado.
3. Remova o atalho da pasta de inicialização (se configurado).

---

## ❓ Perguntas Frequentes

### **📊 Desempenho e Recursos**

- **O programa deixa o computador lento?** Não. Ele consome o mínimo de memória e só trabalha por alguns segundos nos horários agendados.
- **Quanto de internet ele usa?** Apenas algumas KB por verificação, para acessar o site da Receita Federal.

### **🔧 Funcionalidade**

- **Ele instala a ECF sozinho?** Não. Ele apenas avisa e facilita o acesso ao download oficial. A instalação da ECF continua sendo feita por você normalmente.
- **Precisa de permissão de administrador?** A execução normal não requer privilégios elevados.

### **👁️ Monitoramento**

- **Como sei que ele está funcionando?** Verifique se o ícone está visível perto do relógio do Windows.
- **Ele funciona com o computador em suspensão?** Não. O programa precisa que o computador esteja ligado e conectado à internet para verificar atualizações.

### **⚙️ Configuração**

- **Posso mudar o intervalo de verificação?** Sim. Clique com o botão direito no ícone > Configurações.
- **O que acontece se eu mover a pasta do programa?** Você precisará reconfigurar o caminho da ECF e o início automático com Windows.
- **Minha alteração de intervalo é mantida?** Sim. Depois que você salva um intervalo em Configurações, esse valor passa a ter prioridade sobre o que está publicado no Gist, e continua valendo depois de reiniciar o programa.

### **🧬 Executável nativo (`.exe`)**

- **O `.exe` precisa de Java instalado?** Não. Ele foi gerado com GraalVM Native Image e roda direto.
- **No `.exe` a bandeja e a tela de Configurações funcionam?** Ainda não. Essas duas partes usam AWT/Swing, que o Native Image não suporta. O popup de atualização funciona normalmente porque é 100% nativo do Windows (TaskDialog via JNA). Use o `.jar` se precisar da bandeja.
- **Como gero o `.exe`?** Com GraalVM 25: `mvn -Pnative -DskipTests package`. Passo a passo em `docs/NATIVE.md`.

---

## 🐛 Suporte e Problemas Conhecidos

### **Problemas Comuns:**

1. **Ícone não aparece na bandeja:** verifique se o programa está em execução no Gerenciador de Tarefas.
2. **Não detecta a ECF instalada:** verifique a chave `Ecf.InstallPath` no [Gist](https://gist.github.com/Krisner94/e2e1820cb36f7bee41a5212fe9dbad35).
3. **Não inicia com o Windows:** verifique se o atalho está na pasta `shell:startup`.

### **Como Reportar Problemas:**

1. Verifique se está usando a versão mais recente.
2. Consulte as [Perguntas Frequentes](#-perguntas-frequentes).
3. Se o problema persistir, abra uma [issue no GitHub](https://github.com/Krisner94/ECF-Notificador/issues).

---

## 📄 Licença e Informações Técnicas

- **Versão Atual:** 1.0.0
- **Plataforma:** Windows 10/11
- **Tecnologia:** Java 25 (Maven) com JNA e FlatLaf
- **Empacotamento:** fat JAR (Shade) ou executável nativo (GraalVM Native Image)
- **Qualidade:** JUnit 5 (testes parametrizados), Checkstyle, PMD, SpotBugs, JaCoCo
- **Licença:** MIT
- **Repositório:** [github.com/Krisner94/ECF-Notificador](https://github.com/Krisner94/ECF-Notificador)

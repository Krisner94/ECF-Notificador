# 🚀 ECF-Notificador - Guia do Usuário

O **ECF-Notificador** é um assistente digital que monitora automaticamente o site da Receita Federal para você. Ele verifica se existe uma nova versão do programa **ECF (Escrituração Contábil Fiscal)** disponível, evitando que você precise checar o site manualmente todos os dias.

[DOWNLOAD v1.0.0](https://github.com/Krisner94/ECF-Notificador/releases/download/v1.0.0/ecf-notificador-setup.exe)


---

## 📦 1. Métodos de Instalação

### **Opção A: Instalador Recomendado (Windows)**
Para uma instalação completa e profissional, use o instalador oficial:

1. **Baixe o instalador:** Acesse a [página de releases](https://github.com/Krisner94/ECF-Notificador/releases) e baixe o arquivo `ecf-notificador-setup.exe`
2. **Execute o instalador:** Dê um clique duplo no arquivo baixado
3. **Siga o assistente:** O instalador irá:
   - Instalar o programa em `C:\Program Files\ECF Notificador`
   - Criar atalhos no Menu Iniciar
   - Oferecer opção de atalho na Área de Trabalho
   - Configurar para iniciar automaticamente com o Windows (opcional)
   - Criar entrada no Painel de Controle para desinstalação

### **Opção B: Instalação Manual (Portátil)**
Para quem prefere uma versão portátil sem instalação:

1. **Baixe o arquivo ZIP:** Acesse a [página de releases](https://github.com/Krisner94/ECF-Notificador/releases) e baixe o arquivo ZIP
2. **Extraia os arquivos:** Descompacte em uma pasta segura (ex: `C:\Programas\ECF-Notificador` ou em `Documentos`)
3. **Execute o programa:** Dê um clique duplo em `ECF-Notificador.exe`
   - *Atenção: Não mova a pasta de lugar após configurá-la para não interromper o funcionamento automático.*

---

## ⚙️ 2. Configuração Inicial

Para que o programa saiba qual versão você já tem instalada, siga estes passos:

1. **Localize o arquivo de configuração:**
   - **Instalação via instalador:** `C:\Program Files\ECF Notificador\config\appSettings.json`
   - **Instalação manual:** `[sua-pasta]\config\appSettings.json`

2. **Edite o arquivo:**
   - Clique com o botão direito no arquivo **appSettings.json**
   - Escolha **Abrir com > Bloco de Notas** (ou seu editor de texto preferido)
   - Verifique a linha `"InstallPath"`. Ela deve conter o caminho onde sua ECF está instalada:
     ```json
     "InstallPath": "C:\\Arquivos de Programas RFB\\Programas SPED\\SpedECF\\.install4j\\response.varfile"
     ```
   - **Salve** o arquivo e feche o editor

3. **Verificação automática:** Na primeira execução, o programa tentará detectar automaticamente o caminho da ECF se ela estiver instalada no local padrão.

---

## 🖥️ 3. Como Usar o Programa

### **Primeira Execução**
1. **Abrir o programa:** Dê um clique duplo em `ECF-Notificador.exe` (ou use o atalho criado)
2. **Ícone na Barra de Tarefas:** O programa roda discretamente ao lado do relógio do Windows (na bandeja do sistema)
3. **Configuração inicial:** Na primeira execução, verifique se o caminho da ECF está correto nas configurações

### **Menu de Opções**
Clique com o **botão direito** no ícone na bandeja do sistema para acessar:

- **📡 Verificar agora:** Faz uma busca imediata por atualizações no site da Receita Federal
- **⚙️ Configurações:** Define o intervalo de verificação automática (ex: a cada 6 horas)
- **🔄 Reiniciar:** Reinicia o programa (útil após alterações de configuração)
- **❌ Sair:** Fecha o programa totalmente

### **Funcionamento Automático**
- O programa verifica atualizações automaticamente no intervalo configurado
- Opera em segundo plano com consumo mínimo de recursos
- Mostra notificações apenas quando necessário

---

## 🔔 4. Entendendo o Aviso de Atualização

Quando uma nova versão da ECF for detectada e for diferente da que você já usa, um aviso aparecerá no canto da sua tela:

### **Características do Aviso:**
- **🔒 Aviso Fixo:** A mensagem **não desaparece sozinha**, garantindo que você veja a informação
- **📥 Botão "Baixar agora":** Abre seu navegador diretamente na página oficial de downloads da Receita Federal
- **❌ Botão "Fechar":** Remove o aviso da tela, mantendo o programa monitorando em segundo plano

### **O que acontece quando há atualização:**
1. O programa detecta uma nova versão no site da Receita Federal
2. Compara com a versão instalada no seu computador
3. Se for diferente, exibe a notificação com os detalhes
4. Você pode clicar em "Baixar agora" para acessar o download oficial

---

## 🔄 5. Iniciando Automaticamente com o Windows

### **Para instalação via instalador:**
- Durante a instalação, marque a opção **"Iniciar com o Windows"**
- O instalador configura automaticamente o registro necessário

### **Para instalação manual:**
1. Clique com o **botão direito** em `ECF-Notificador.exe` e selecione **Criar Atalho**
2. Pressione **Windows + R**, digite `shell:startup` e pressione **Enter**
3. Cole o **atalho** dentro desta pasta

---

## 🗑️ 6. Como Desinstalar

### **Para instalação via instalador:**
1. Acesse **Painel de Controle > Programas > Programas e Recursos**
2. Localize **"ECF Notificador"** na lista
3. Clique em **Desinstalar** e siga as instruções

### **Para instalação manual:**
1. Feche o programa (botão direito no ícone > Sair)
2. Exclua a pasta onde o programa está instalado
3. Remova o atalho da pasta de inicialização (se configurado)

---

## ❓ Perguntas Frequentes

### **📊 Desempenho e Recursos**
- **O programa deixa o computador lento?** Não. Ele consome o mínimo de memória e só trabalha por alguns segundos nos horários agendados.
- **Quanto de internet ele usa?** Apenas algumas KB por verificação, para acessar o site da Receita Federal.

### **🔧 Funcionalidade**
- **Ele instala a ECF sozinho?** Não. Ele apenas te avisa e facilita o acesso ao download oficial. A instalação da ECF continua sendo feita por você normalmente.
- **Precisa de permissão de administrador?** Apenas durante a instalação via instalador. A execução normal não requer privilégios elevados.

### **👁️ Monitoramento**
- **Como sei que ele está funcionando?** Verifique se o ícone está visível perto do relógio do Windows.
- **Ele funciona com o computador em suspensão?** Não. O programa precisa que o computador esteja ligado e conectado à internet para verificar atualizações.

### **⚙️ Configuração**
- **Posso mudar o intervalo de verificação?** Sim. Clique com o botão direito no ícone > Configurações.
- **O que acontece se eu mover a pasta do programa?** Na instalação manual, você precisará reconfigurar o caminho da ECF e o início automático com Windows.

---

## 🐛 Suporte e Problemas Conhecidos

### **Problemas Comuns:**
1. **Ícone não aparece na bandeja:** Verifique se o programa está em execução no Gerenciador de Tarefas.
2. **Não detecta a ECF instalada:** Verifique se o caminho em `appSettings.json` está correto.
3. **Não inicia com o Windows:** Para instalação manual, verifique se o atalho está na pasta `shell:startup`.

### **Como Reportar Problemas:**
1. Verifique se está usando a versão mais recente
2. Consulte as [Perguntas Frequentes](#-perguntas-frequentes)
3. Se o problema persistir, abra uma [issue no GitHub](https://github.com/Krisner94/ECF-Notificador/issues)

---

## 📄 Licença e Informações Técnicas

- **Versão Atual:** 1.0.0
- **Plataforma:** Windows 10/11
- **Tecnologia:** .NET 8.0
- **Licença:** MIT
- **Repositório:** [github.com/Krisner94/ECF-Notificador](https://github.com/Krisner94/ECF-Notificador)

---

**✨ Dica:** Para uma experiência completa, recomendamos usar o instalador oficial que inclui todas as dependências necessárias e configuração automática.
# 🚀 ECF-Notificador - Guia do Usuário

O **ECF-Notificador** é um assistente digital que monitora automaticamente o site da Receita Federal para você. Ele verifica se existe uma nova versão do programa **ECF (Escrituração Contábil Fiscal)** disponível, evitando que você precise checar o site manualmente todos os dias.

---

## 📦 1. Como Instalar

Este programa é uma ferramenta leve e não exige uma instalação complexa:

1.  **Copie a pasta:** Salve a pasta completa do programa em um local seguro no seu computador (ex: `C:\Programas\ECF-Notificador` ou em sua pasta `Documentos`).
    * *Atenção: Não mova a pasta de lugar após configurá-la para não interromper o funcionamento automático.*
2.  **Arquivos Principais:** Para o programa funcionar, mantenha sempre estes itens na mesma pasta:
    * `ECF-Notificador.exe` (O programa que você abre).
    * Pasta `config` (Onde ficam guardadas suas preferências).

---

## ⚙️ 2. Configuração Inicial

Para que o programa saiba qual versão você já tem instalada, siga estes passos:

1.  Abra a pasta **config** e clique com o botão direito no arquivo **appSettings.json**. 
2.  Escolha a opção **Abrir com > Bloco de Notas**.
3.  Verifique a linha `InstallPath`. Ela deve conter o caminho onde a sua ECF está instalada no computador.
    * *Caminho padrão:* `C:\\Arquivos de Programas RFB\\Programas SPED\\SpedECF\\.install4j\\response.varfile`.
4.  **Salve** o arquivo e feche o Bloco de Notas.

---

## 🖥️ 3. Como Usar o Programa

1.  **Abrir o programa:** Dê um clique duplo em `ECF-Notificador.exe`.
2.  **Ícone na Barra de Tarefas:** O programa roda de forma discreta ao lado do relógio do Windows (na bandeja do sistema).
3.  **Menu de Opções:** Clique com o **botão direito** no ícone para acessar as funções:
    * **Verificar agora:** Faz uma busca imediata por atualizações no site da Receita.
    * **Configurações:** Define de quanto em quanto tempo o programa deve trabalhar sozinho (ex: a cada 6 horas).
    * **Sair:** Fecha o programa totalmente.

---

## 🔔 4. Entendendo o Aviso de Atualização

Sempre que uma versão nova for detectada e for diferente da que você já usa, um aviso aparecerá no canto da sua tela.

* **Aviso Fixo:** A mensagem **não desaparece sozinha**, garantindo que você veja a informação.
* **Botão "Baixar agora":** Abre o seu navegador diretamente na página oficial de downloads da Receita Federal.
* **Botão "Fechar":** Remove o aviso da tela, mas mantém o programa monitorando em segundo plano.

---

## 🔄 5. Iniciando com o Windows

Para que o programa abra sozinho sempre que você ligar o computador:

1.  Clique com o **botão direito** no arquivo `ECF-Notificador.exe` e selecione **Criar Atalho**.
2.  No teclado, segure a tecla **Windows** e aperte a letra **R**.
3.  Na caixa que abrir, digite `shell:startup` e aperte **Enter**.
4.  Cole o **atalho** que você criou dentro desta pasta.

---

## ❓ Perguntas Frequentes

* **O programa deixa o computador lento?** Não. Ele consome o mínimo de memória e só trabalha por alguns segundos nos horários agendados.
* **Ele instala a ECF sozinho?** Não. Ele apenas te avisa e facilita o acesso ao download oficial. A instalação da ECF continua sendo feita por você normalmente.
* **Como sei que ele está funcionando?** Verifique se o ícone está visível perto do relógio do Windows.
# Instruções para Criar a Release 1.0.0 no GitHub

## 📋 Pré-requisitos

1. **Token de acesso pessoal do GitHub** com permissão `repo`
   - Acesse: https://github.com/settings/tokens
   - Clique em "Generate new token (classic)"
   - Selecione a permissão `repo` (controle total de repositórios privados)
   - Copie o token gerado (guarde em local seguro)

2. **Arquivo executável** já criado:
   - Local: `installer\output\ecf-notificador-setup.exe`
   - Tamanho: ~53.5 MB
   - Status: ✅ Disponível

## 🚀 Passo a Passo

### Opção 1: Usar o Script PowerShell (Recomendado)

1. **Abra o PowerShell como Administrador**
   - Pressione `Windows + X`
   - Selecione "Windows PowerShell (Admin)" ou "Terminal (Admin)"

2. **Navegue até a pasta do projeto:**
   ```powershell
   cd "C:\Users\Rhama\Documents\GitHub\ECF-Notificador"
   ```

3. **Execute o script com seu token:**
   ```powershell
   .\create_release.ps1 -GitHubToken "seu_token_aqui"
   ```

4. **Aguarde a execução** - O script irá:
   - Criar a release v1.0.0 no GitHub
   - Upload do arquivo executável
   - Mostrar a URL da release criada

### Opção 2: Criar Manualmente pelo GitHub Web

1. **Acesse o repositório:** https://github.com/Krisner94/ECF-Notificador

2. **Clique em "Releases"** no lado direito

3. **Clique em "Draft a new release"**

4. **Preencha os dados:**
   - **Tag version:** `v1.0.0` (já existe)
   - **Release title:** `ECF-Notificador 1.0.0`
   - **Description:** Copie o conteúdo abaixo:

   ```
   ## 🚀 ECF-Notificador 1.0.0

   ### Novidades nesta versão:
   - **Instalador oficial para Windows** criado com Inno Setup
   - Instalação completa em `C:\Program Files\ECF Notificador`
   - Atalhos no Menu Iniciar e Área de Trabalho
   - Configuração automática para iniciar com o Windows
   - Interface em português brasileiro
   - Todas as dependências incluídas

   ### 📦 Funcionalidades:
   - Monitoramento automático do site da Receita Federal
   - Notificações Toast que não desaparecem automaticamente
   - Interface discreta na bandeja do sistema
   - Configuração personalizável do intervalo de verificação
   - Botão de download direto para a página oficial
   - Detecção automática da versão instalada da ECF

   ### ⚙️ Requisitos do Sistema:
   - Windows 10 ou 11
   - .NET 8.0 Runtime (incluído no instalador)
   - Conexão com internet para verificação de atualizações

   ### 📄 Documentação:
   Consulte o [README.md](https://github.com/Krisner94/ECF-Notificador/blob/main/README.md) para instruções detalhadas de instalação e uso.

   ### 🔗 Links:
   - [Código-fonte](https://github.com/Krisner94/ECF-Notificador)
   - [Issues e suporte](https://github.com/Krisner94/ECF-Notificador/issues)
   - [Changelog completo](https://github.com/Krisner94/ECF-Notificador/blob/main/CHANGELOG.md)
   ```

5. **Arraste o arquivo** `installer\output\ecf-notificador-setup.exe` para a área de upload

6. **Clique em "Publish release"**

## ✅ Verificação

Após criar a release, verifique:

1. **URL da release:** https://github.com/Krisner94/ECF-Notificador/releases/tag/v1.0.0
2. **Arquivo disponível para download:** `ecf-notificador-setup.exe`
3. **Documentação atualizada:** README.md aponta para a release

## 🐛 Solução de Problemas

### Erro "PowerShell execution policy"
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### Erro de autenticação
- Verifique se o token tem permissão `repo`
- O token deve ser do tipo "classic" (não fine-grained)

### Arquivo muito grande
- O GitHub tem limite de 2GB por arquivo (nosso arquivo tem 53.5 MB, OK)

## 📁 Arquivos Criados

- `create_release.ps1` - Script para automatizar a criação
- `CHANGELOG.md` - Histórico completo de mudanças
- `README.md` - Documentação atualizada com instruções do instalador

## 🎉 Conclusão

A release 1.0.0 está pronta para ser publicada! O projeto agora tem:

1. ✅ Tag v1.0.0 criada e enviada para o GitHub
2. ✅ Documentação completa e atualizada
3. ✅ Instalador profissional criado
4. ✅ Changelog detalhado
5. ✅ Script de automação para criação da release

Execute o script PowerShell ou crie a release manualmente pelo site do GitHub.
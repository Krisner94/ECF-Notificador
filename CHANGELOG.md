# Changelog

Todas as mudanças notáveis neste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/),
e este projeto adere ao [Versionamento Semântico](https://semver.org/lang/pt-BR/).

## [1.0.0] - 2026-04-23

### 🚀 Adicionado
- **Instalador oficial para Windows**: Instalador completo criado com Inno Setup
  - Instalação em `C:\Program Files\ECF Notificador`
  - Criação de atalhos no Menu Iniciar
  - Opção de atalho na Área de Trabalho
  - Configuração automática para iniciar com o Windows
  - Entrada no Painel de Controle para desinstalação fácil
  - Interface em português brasileiro
- **Arquivos de dependências**: Todas as DLLs necessárias incluídas
- **Script de instalação**: `setup.iss` para construção do instalador
- **Documentação completa**: Guia do usuário atualizado com instruções detalhadas

### 🔧 Melhorado
- **Documentação**: README.md completamente reformulado e expandido
  - Adicionada seção de métodos de instalação (instalador vs portátil)
  - Guia de configuração inicial mais detalhado
  - Instruções de uso aprimoradas
  - Seção de perguntas frequentes expandida
  - Informações sobre desinstalação
  - Seção de suporte e problemas conhecidos
- **Configuração**: Verificação automática do caminho da ECF na primeira execução
- **Experiência do usuário**: Instalação mais profissional e completa

### 📦 Funcionalidades Existentes (Mantidas)
- **Monitoramento automático**: Verifica atualizações da ECF no site da Receita Federal
- **Notificações Toast**: Avisos fixos que não desaparecem automaticamente
- **Interface Tray Icon**: Execução discreta na bandeja do sistema
- **Configuração de intervalo**: Personalização do tempo entre verificações
- **Início automático com Windows**: Configuração para execução automática
- **Botão de download direto**: Acesso rápido à página oficial de downloads

### 🐛 Correções
- Nenhuma correção nesta versão (primeira release oficial)

### ⚠️ Notas de Lançamento
Esta é a primeira versão estável do ECF-Notificador. O projeto agora inclui um instalador profissional que facilita a distribuição e instalação para usuários finais.

**Requisitos do Sistema:**
- Windows 10 ou 11
- .NET 8.0 Runtime (incluído no instalador)
- Conexão com internet para verificação de atualizações

**Arquivos Incluídos na Release:**
- `ecf-notificador-setup.exe` - Instalador oficial
- Código-fonte completo
- Documentação atualizada

**Links:**
- [Repositório GitHub](https://github.com/Krisner94/ECF-Notificador)
- [Issues e Suporte](https://github.com/Krisner94/ECF-Notificador/issues)

---

## Histórico de Desenvolvimento

### [0.1.0] - 2026-04-22
- **Versão inicial funcional**: Primeira implementação do ECF-Notificador
- **Funcionalidades básicas**:
  - Monitoramento do site da Receita Federal
  - Notificações Toast com botão de download
  - Interface Tray Icon com menu de contexto
  - Configuração de intervalo de verificação
  - Detecção da versão instalada da ECF
- **Arquitetura**:
  - Desenvolvido em C# com .NET 8.0
  - Serviços separados para versão, notificação e configuração
  - Configuração via arquivo JSON
  - Ícone personalizado

---

## Próximas Versões (Planejadas)

### [1.1.0] - Planejado
- **Melhorias na interface**: Configurações mais intuitivas
- **Logs detalhados**: Sistema de logging para diagnóstico
- **Verificação de integridade**: Validação dos arquivos instalados
- **Atualizações automáticas**: Sistema de auto-atualização do próprio notificador

### [2.0.0] - Planejado
- **Suporte multiplataforma**: Versões para Linux e macOS
- **Interface web**: Painel de controle via navegador
- **API REST**: Integração com outros sistemas
- **Notificações push**: Alertas via email ou mensagens

---

## Como Contribuir

1. Faça um fork do repositório
2. Crie uma branch para sua feature (`git checkout -b feature/nova-feature`)
3. Commit suas mudanças (`git commit -m 'Adiciona nova feature'`)
4. Push para a branch (`git push origin feature/nova-feature`)
5. Abra um Pull Request

---

## Licença

Este projeto está licenciado sob a licença MIT - veja o arquivo [LICENSE](LICENSE) para detalhes.
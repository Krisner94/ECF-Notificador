# Script PowerShell para criar release no GitHub
# Requer token de acesso pessoal do GitHub com permissão 'repo'

param(
    [string]$GitHubToken,
    [string]$Tag = "v1.0.0",
    [string]$ReleaseName = "ECF-Notificador 1.0.0",
    [string]$AssetPath = "installer\output\ecf-notificador-setup.exe"
)

# Verificar se o token foi fornecido
if (-not $GitHubToken) {
    Write-Host "Erro: Token do GitHub não fornecido." -ForegroundColor Red
    Write-Host "Uso: .\create_release.ps1 -GitHubToken 'seu_token_aqui'" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Para criar um token de acesso pessoal do GitHub:" -ForegroundColor Cyan
    Write-Host "1. Acesse https://github.com/settings/tokens" -ForegroundColor Cyan
    Write-Host "2. Clique em 'Generate new token (classic)'" -ForegroundColor Cyan
    Write-Host "3. Selecione a permissão 'repo'" -ForegroundColor Cyan
    Write-Host "4. Copie o token gerado" -ForegroundColor Cyan
    exit 1
}

# Verificar se o arquivo de asset existe
if (-not (Test-Path $AssetPath)) {
    Write-Host "Erro: Arquivo de asset não encontrado: $AssetPath" -ForegroundColor Red
    exit 1
}

# Configurar variáveis
$RepoOwner = "Krisner94"
$RepoName = "ECF-Notificador"
$ApiUrl = "https://api.github.com/repos/$RepoOwner/$RepoName/releases"

# Criar corpo da release (baseado no CHANGELOG.md)
$ReleaseBody = @"
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
"@

# Criar payload JSON
$ReleaseData = @{
    tag_name = $Tag
    name = $ReleaseName
    body = $ReleaseBody
    draft = $false
    prerelease = $false
    generate_release_notes = $false
} | ConvertTo-Json

Write-Host "Criando release $Tag no GitHub..." -ForegroundColor Cyan

# Fazer requisição para criar a release
try {
    $Response = Invoke-RestMethod -Uri $ApiUrl `
        -Method POST `
        -Headers @{
            "Authorization" = "token $GitHubToken"
            "Accept" = "application/vnd.github.v3+json"
        } `
        -Body $ReleaseData `
        -ContentType "application/json"

    $ReleaseId = $Response.id
    $ReleaseUrl = $Response.html_url
    
    Write-Host "✅ Release criada com sucesso!" -ForegroundColor Green
    Write-Host "ID da Release: $ReleaseId" -ForegroundColor Cyan
    Write-Host "URL: $ReleaseUrl" -ForegroundColor Cyan

    # Upload do asset
    Write-Host "`nEnviando arquivo executável..." -ForegroundColor Cyan
    
    $AssetName = Split-Path $AssetPath -Leaf
    $UploadUrl = "https://uploads.github.com/repos/$RepoOwner/$RepoName/releases/$ReleaseId/assets?name=$AssetName"
    
    $AssetBytes = [System.IO.File]::ReadAllBytes((Resolve-Path $AssetPath))
    
    $UploadResponse = Invoke-RestMethod -Uri $UploadUrl `
        -Method POST `
        -Headers @{
            "Authorization" = "token $GitHubToken"
            "Accept" = "application/vnd.github.v3+json"
            "Content-Type" = "application/octet-stream"
        } `
        -Body $AssetBytes
    
    Write-Host "✅ Arquivo enviado com sucesso!" -ForegroundColor Green
    Write-Host "Asset: $($UploadResponse.browser_download_url)" -ForegroundColor Cyan
    
    Write-Host "`n🎉 Release publicada com sucesso!" -ForegroundColor Green
    Write-Host "Acesse: $ReleaseUrl" -ForegroundColor Yellow
    
} catch {
    Write-Host "❌ Erro ao criar release:" -ForegroundColor Red
    Write-Host "Status Code: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Red
    Write-Host "Mensagem: $($_.Exception.Message)" -ForegroundColor Red
    
    if ($_.Exception.Response) {
        $ErrorStream = $_.Exception.Response.GetResponseStream()
        $Reader = New-Object System.IO.StreamReader($ErrorStream)
        $ErrorBody = $Reader.ReadToEnd()
        $Reader.Close()
        Write-Host "Detalhes: $ErrorBody" -ForegroundColor Red
    }
    
    exit 1
}
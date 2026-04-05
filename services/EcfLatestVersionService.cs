using System.Net.Http;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using System;

namespace ECF_Notificador.services
{
    public class EcfLatestVersionService
    {
        private readonly HttpClient _http = new();

        public async Task<string?> GetLatestVersion(string url, string className)
        {
            try
            {
                // Adiciona um User-Agent para evitar bloqueios do portal da Receita
                _http.DefaultRequestHeaders.UserAgent.ParseAdd("Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                
                var html = await _http.GetStringAsync(url);

                // O padrão .*? garante que ele pule a palavra "Versão" e pegue apenas os números
                string pattern = $@"{className}[^>]*>.*?(\d+\.\d+\.\d+)";
                var match = Regex.Match(html, pattern, RegexOptions.IgnoreCase | RegexOptions.Singleline);

                if (match.Success)
                {
                    return match.Groups[1].Value.Trim(); // Retorna "12.0.3"
                }
                
                return null;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Erro ao acessar site: {ex.Message}");
                return null;
            }
        }
    }
}
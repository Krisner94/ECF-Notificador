using System.Text.RegularExpressions;

public class EcfVersionService
{
    public string? GetInstalledVersion(string path)
    {
        if (!File.Exists(path)) {
            return null;
        }            

        var content = File.ReadAllText(path);

        var match = Regex.Match(content, @"([\d]+\.[\d]+(\.[\d]+)?)");

        return match.Success ? match.Value : null;
    }
}
namespace Glypdl.Windows.Models;

public class CookieProfile
{
    public string Id { get; set; } = Guid.NewGuid().ToString();
    public string Name { get; set; } = string.Empty;
    public string FilePath { get; set; } = string.Empty;
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public bool Exists => !string.IsNullOrWhiteSpace(FilePath) && File.Exists(FilePath);
}

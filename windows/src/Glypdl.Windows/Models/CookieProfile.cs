namespace Glypdl.Windows.Models;

public class CookieProfile : CommunityToolkit.Mvvm.ComponentModel.ObservableObject
{
    public string Id { get; set; } = Guid.NewGuid().ToString();
    public string Name { get; set; } = string.Empty;
    public string FilePath { get; set; } = string.Empty;
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public bool Exists => !string.IsNullOrWhiteSpace(FilePath) && File.Exists(FilePath);

    private bool _isActive;
    public bool IsActive
    {
        get => _isActive;
        set => SetProperty(ref _isActive, value);
    }
}

public class BrowserInfo
{
    public string Id { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public bool IsInstalled { get; set; }
    public List<string> Profiles { get; set; } = new();
    public string DisplayName => IsInstalled ? Name : $"{Name} (Not Installed)";
    public override string ToString() => DisplayName;
}

public class KeyringInfo
{
    public string Id { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public override string ToString() => Name;
}

public class CookieOptionItem
{
    public string Type { get; set; } = "none"; // "none", "browser", "file"
    public string DisplayName { get; set; } = string.Empty;
    public string FilePath { get; set; } = string.Empty;
    public string Spec { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public override string ToString() => DisplayName;
}


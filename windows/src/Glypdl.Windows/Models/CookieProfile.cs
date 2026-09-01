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

public class CookieOptionItem
{
    public string DisplayName { get; set; } = string.Empty;
    public string FilePath { get; set; } = string.Empty;
    public override string ToString() => DisplayName;
}


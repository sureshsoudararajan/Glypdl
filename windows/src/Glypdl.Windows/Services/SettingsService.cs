using System.Text.Json;
using Glypdl.Windows.Models;
using Glypdl.Windows.Utilities;

namespace Glypdl.Windows.Services;

public class SettingsService : ISettingsService
{
    private AppSettings? _cachedSettings;
    private static readonly JsonSerializerOptions JsonOptions = new() { WriteIndented = true };

    public AppSettings GetSettings()
    {
        if (_cachedSettings != null) return _cachedSettings;

        string path = PathUtils.GetConfigFilePath();
        if (File.Exists(path))
        {
            try
            {
                string json = File.ReadAllText(path);
                _cachedSettings = JsonSerializer.Deserialize<AppSettings>(json, JsonOptions);
            }
            catch { }
        }

        _cachedSettings ??= new AppSettings
        {
            DownloadDirectory = PathUtils.GetDefaultDownloadDirectory()
        };

        return _cachedSettings;
    }

    public void SaveSettings(AppSettings settings)
    {
        _cachedSettings = settings;
        try
        {
            string path = PathUtils.GetConfigFilePath();
            var dir = Path.GetDirectoryName(path);
            if (!string.IsNullOrWhiteSpace(dir))
            {
                Directory.CreateDirectory(dir);
            }
            string json = JsonSerializer.Serialize(settings, JsonOptions);
            File.WriteAllText(path, json);
        }
        catch { }
    }
}

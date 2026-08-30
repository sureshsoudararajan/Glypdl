using System.Text.Json;
using Glypdl.Windows.Models;
using Glypdl.Windows.Utilities;

namespace Glypdl.Windows.Services;

public class CookieService : ICookieService
{
    private readonly ISettingsService _settingsService;
    private List<CookieProfile>? _profiles;

    public CookieService(ISettingsService settingsService)
    {
        _settingsService = settingsService;
    }

    public List<CookieProfile> GetProfiles()
    {
        if (_profiles != null) return _profiles;

        string path = PathUtils.GetProfilesFilePath();
        if (File.Exists(path))
        {
            try
            {
                string json = File.ReadAllText(path);
                _profiles = JsonSerializer.Deserialize<List<CookieProfile>>(json);
            }
            catch { }
        }

        _profiles ??= new List<CookieProfile>();
        return _profiles;
    }

    public void AddProfile(string name, string filePath)
    {
        var profiles = GetProfiles();
        profiles.Add(new CookieProfile
        {
            Name = name,
            FilePath = filePath,
            CreatedAt = DateTime.UtcNow
        });
        SaveProfiles();
    }

    public void RemoveProfile(string profileId)
    {
        var profiles = GetProfiles();
        profiles.RemoveAll(p => p.Id == profileId);
        SaveProfiles();
    }

    public CookieProfile? GetActiveProfile()
    {
        var settings = _settingsService.GetSettings();
        if (!settings.UseCookies || string.IsNullOrWhiteSpace(settings.ActiveCookieProfileId))
        {
            return null;
        }

        return GetProfiles().FirstOrDefault(p => p.Id == settings.ActiveCookieProfileId && p.Exists);
    }

    private void SaveProfiles()
    {
        try
        {
            string path = PathUtils.GetProfilesFilePath();
            string json = JsonSerializer.Serialize(_profiles, new JsonSerializerOptions { WriteIndented = true });
            File.WriteAllText(path, json);
        }
        catch { }
    }
}

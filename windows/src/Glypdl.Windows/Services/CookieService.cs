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
        if (!settings.UseCookies)
        {
            return null;
        }

        if (!string.IsNullOrWhiteSpace(settings.ActiveCookieProfileId))
        {
            var p = GetProfiles().FirstOrDefault(x => (x.Id == settings.ActiveCookieProfileId || x.Name.Equals(settings.ActiveCookieProfileId, StringComparison.OrdinalIgnoreCase)) && x.Exists);
            if (p != null) return p;
        }

        if (!string.IsNullOrWhiteSpace(settings.ActiveCookieFile) && ValidateCookieFile(settings.ActiveCookieFile))
        {
            return new CookieProfile
            {
                Id = "custom",
                Name = "Active Cookie File",
                FilePath = settings.ActiveCookieFile
            };
        }

        return null;
    }

    public string? GetActiveCookiePath()
    {
        var settings = _settingsService.GetSettings();
        if (!settings.UseCookies) return null;

        if (!string.IsNullOrWhiteSpace(settings.ActiveCookieFile) && ValidateCookieFile(settings.ActiveCookieFile))
        {
            return settings.ActiveCookieFile;
        }

        return GetActiveProfile()?.FilePath;
    }

    public bool ValidateCookieFile(string? path)
    {
        if (string.IsNullOrWhiteSpace(path)) return false;
        try
        {
            return File.Exists(path);
        }
        catch
        {
            return false;
        }
    }

    public List<string> GetCookieArguments(string? profileNameOrId = null, string? customCookieFile = null)
    {
        if (!string.IsNullOrWhiteSpace(customCookieFile) && ValidateCookieFile(customCookieFile))
        {
            return new List<string> { "--cookies", customCookieFile };
        }

        if (!string.IsNullOrWhiteSpace(profileNameOrId))
        {
            var p = GetProfiles().FirstOrDefault(x => x.Id == profileNameOrId || x.Name.Equals(profileNameOrId, StringComparison.OrdinalIgnoreCase));
            if (p != null && ValidateCookieFile(p.FilePath))
            {
                return new List<string> { "--cookies", p.FilePath };
            }
        }

        string? active = GetActiveCookiePath();
        if (!string.IsNullOrWhiteSpace(active) && ValidateCookieFile(active))
        {
            return new List<string> { "--cookies", active };
        }

        return new List<string>();
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

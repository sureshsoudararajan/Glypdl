using Glypdl.Windows.Models;

namespace Glypdl.Windows.Services;

public interface ICookieService
{
    List<CookieProfile> GetProfiles();
    void AddProfile(string name, string filePath);
    void RemoveProfile(string profileId);
    CookieProfile? GetActiveProfile();
    string? GetActiveCookiePath();
    bool ValidateCookieFile(string? path);
    List<string> GetCookieArguments(string? profileNameOrId = null, string? customCookieFile = null, string? browserSpec = null);

    List<BrowserInfo> GetSupportedBrowsers();
    List<KeyringInfo> GetSupportedKeyrings();
    List<BrowserInfo> DiscoverInstalledBrowsers(bool forceRefresh = false);
    string BuildBrowserSpec(string browserName, string? profile = null, string? keyring = null);
    Task<(bool Success, string Message, string TechnicalDetails)> TestBrowserCookiesAsync(string browserSpec, string testUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ");
}

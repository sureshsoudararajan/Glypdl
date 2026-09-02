using System.Diagnostics;
using System.Text.Json;
using System.Text.RegularExpressions;
using Glypdl.Windows.Models;
using Glypdl.Windows.Utilities;

namespace Glypdl.Windows.Services;

public class CookieService : ICookieService
{
    private readonly ISettingsService _settingsService;
    private readonly IYtDlpService? _ytdlpService;
    private List<CookieProfile>? _profiles;
    private List<BrowserInfo>? _cachedBrowsers;

    private static readonly List<BrowserInfo> SupportedBrowsersList = new()
    {
        new BrowserInfo { Id = "edge", Name = "Microsoft Edge" },
        new BrowserInfo { Id = "chrome", Name = "Google Chrome" },
        new BrowserInfo { Id = "firefox", Name = "Mozilla Firefox" },
        new BrowserInfo { Id = "brave", Name = "Brave Browser" },
        new BrowserInfo { Id = "opera", Name = "Opera" },
        new BrowserInfo { Id = "vivaldi", Name = "Vivaldi" },
        new BrowserInfo { Id = "chromium", Name = "Chromium" },
        new BrowserInfo { Id = "librewolf", Name = "LibreWolf" }
    };

    private static readonly List<KeyringInfo> SupportedKeyringsList = new()
    {
        new KeyringInfo { Id = "auto", Name = "Automatic (Default)" },
        new KeyringInfo { Id = "basictext", Name = "Basic Text (Unencrypted)" }
    };

    public CookieService(ISettingsService settingsService, IYtDlpService? ytdlpService = null)
    {
        _settingsService = settingsService;
        _ytdlpService = ytdlpService;
    }

    public List<BrowserInfo> GetSupportedBrowsers() => new(SupportedBrowsersList);

    public List<KeyringInfo> GetSupportedKeyrings() => new(SupportedKeyringsList);

    public List<BrowserInfo> DiscoverInstalledBrowsers(bool forceRefresh = false)
    {
        if (_cachedBrowsers != null && !forceRefresh)
        {
            return _cachedBrowsers;
        }

        string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        string appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
        string programFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
        string programFilesX86 = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86);

        var discovered = new List<BrowserInfo>();

        foreach (var browser in SupportedBrowsersList)
        {
            var info = new BrowserInfo
            {
                Id = browser.Id,
                Name = browser.Name,
                Profiles = new List<string> { "Default" }
            };

            bool installed = false;
            string? configDir = null;

            switch (browser.Id)
            {
                case "edge":
                    configDir = Path.Combine(localAppData, "Microsoft", "Edge", "User Data");
                    installed = Directory.Exists(configDir) ||
                                File.Exists(Path.Combine(programFilesX86, "Microsoft", "Edge", "Application", "msedge.exe")) ||
                                File.Exists(Path.Combine(programFiles, "Microsoft", "Edge", "Application", "msedge.exe"));
                    break;

                case "chrome":
                    configDir = Path.Combine(localAppData, "Google", "Chrome", "User Data");
                    installed = Directory.Exists(configDir) ||
                                File.Exists(Path.Combine(programFiles, "Google", "Chrome", "Application", "chrome.exe")) ||
                                File.Exists(Path.Combine(programFilesX86, "Google", "Chrome", "Application", "chrome.exe"));
                    break;

                case "firefox":
                    configDir = Path.Combine(appData, "Mozilla", "Firefox");
                    installed = Directory.Exists(configDir) ||
                                File.Exists(Path.Combine(programFiles, "Mozilla Firefox", "firefox.exe")) ||
                                File.Exists(Path.Combine(programFilesX86, "Mozilla Firefox", "firefox.exe"));
                    break;

                case "brave":
                    configDir = Path.Combine(localAppData, "BraveSoftware", "Brave-Browser", "User Data");
                    installed = Directory.Exists(configDir) ||
                                File.Exists(Path.Combine(programFiles, "BraveSoftware", "Brave-Browser", "Application", "brave.exe"));
                    break;

                case "opera":
                    configDir = Path.Combine(appData, "Opera Software", "Opera Stable");
                    if (!Directory.Exists(configDir))
                    {
                        configDir = Path.Combine(appData, "Opera Software", "Opera GX Stable");
                    }
                    installed = Directory.Exists(configDir) ||
                                Directory.Exists(Path.Combine(localAppData, "Programs", "Opera")) ||
                                Directory.Exists(Path.Combine(localAppData, "Programs", "Opera GX"));
                    break;

                case "vivaldi":
                    configDir = Path.Combine(localAppData, "Vivaldi", "User Data");
                    installed = Directory.Exists(configDir) ||
                                Directory.Exists(Path.Combine(localAppData, "Programs", "Vivaldi"));
                    break;

                case "chromium":
                    configDir = Path.Combine(localAppData, "Chromium", "User Data");
                    installed = Directory.Exists(configDir);
                    break;

                case "librewolf":
                    configDir = Path.Combine(appData, "librewolf");
                    installed = Directory.Exists(configDir) ||
                                File.Exists(Path.Combine(programFiles, "LibreWolf", "librewolf.exe"));
                    break;
            }

            info.IsInstalled = installed;

            if (configDir != null && Directory.Exists(configDir))
            {
                if (browser.Id == "firefox" || browser.Id == "librewolf")
                {
                    var profiles = DiscoverFirefoxProfiles(configDir);
                    if (profiles.Count > 0) info.Profiles = profiles;
                }
                else
                {
                    var profiles = DiscoverChromiumProfiles(configDir);
                    if (profiles.Count > 0) info.Profiles = profiles;
                }
            }

            discovered.Add(info);
        }

        discovered.Sort((a, b) =>
        {
            if (a.IsInstalled != b.IsInstalled) return b.IsInstalled.CompareTo(a.IsInstalled);
            return string.Compare(a.Name, b.Name, StringComparison.OrdinalIgnoreCase);
        });

        _cachedBrowsers = discovered;
        return discovered;
    }

    private static List<string> DiscoverChromiumProfiles(string configDir)
    {
        var profiles = new List<string>();
        try
        {
            string localState = Path.Combine(configDir, "Local State");
            if (File.Exists(localState))
            {
                try
                {
                    string json = File.ReadAllText(localState);
                    using var doc = JsonDocument.Parse(json);
                    if (doc.RootElement.TryGetProperty("profile", out var profElem) &&
                        profElem.TryGetProperty("info_cache", out var infoCache))
                    {
                        foreach (var prop in infoCache.EnumerateObject())
                        {
                            if (!profiles.Contains(prop.Name))
                            {
                                profiles.Add(prop.Name);
                            }
                        }
                    }
                }
                catch { }
            }

            if (profiles.Count == 0)
            {
                if (Directory.Exists(Path.Combine(configDir, "Default")))
                {
                    profiles.Add("Default");
                }
                foreach (var dir in Directory.GetDirectories(configDir))
                {
                    string name = Path.GetFileName(dir);
                    if ((name.StartsWith("Profile ", StringComparison.OrdinalIgnoreCase) || name == "Default") && !profiles.Contains(name))
                    {
                        profiles.Add(name);
                    }
                }
            }
        }
        catch { }

        return profiles.Count > 0 ? profiles : new List<string> { "Default" };
    }

    private static List<string> DiscoverFirefoxProfiles(string configDir)
    {
        var profiles = new List<string>();
        try
        {
            string iniPath = Path.Combine(configDir, "profiles.ini");
            if (File.Exists(iniPath))
            {
                foreach (var line in File.ReadAllLines(iniPath))
                {
                    var trimmed = line.Trim();
                    if (trimmed.StartsWith("Name=", StringComparison.OrdinalIgnoreCase))
                    {
                        string name = trimmed.Substring(5).Trim();
                        if (!string.IsNullOrWhiteSpace(name) && !profiles.Contains(name))
                        {
                            profiles.Add(name);
                        }
                    }
                }
            }

            if (profiles.Count == 0)
            {
                foreach (var dir in Directory.GetDirectories(configDir))
                {
                    if (File.Exists(Path.Combine(dir, "cookies.sqlite")))
                    {
                        string name = Path.GetFileName(dir);
                        if (!profiles.Contains(name)) profiles.Add(name);
                    }
                }
            }
        }
        catch { }

        return profiles.Count > 0 ? profiles : new List<string> { "default-release", "default" };
    }

    public string BuildBrowserSpec(string browserName, string? profile = null, string? keyring = null)
    {
        if (string.IsNullOrWhiteSpace(browserName) || browserName.Equals("none", StringComparison.OrdinalIgnoreCase))
        {
            return string.Empty;
        }

        string bName = browserName.Trim().ToLowerInvariant();
        string keyringSuffix = string.Empty;
        if (!string.IsNullOrWhiteSpace(keyring) && !keyring.Equals("auto", StringComparison.OrdinalIgnoreCase) && !keyring.Equals("default", StringComparison.OrdinalIgnoreCase))
        {
            keyringSuffix = $"+{keyring.Trim().ToLowerInvariant()}";
        }

        string profileSuffix = string.Empty;
        if (!string.IsNullOrWhiteSpace(profile) && !profile.Equals("Default", StringComparison.OrdinalIgnoreCase))
        {
            profileSuffix = $":{profile.Trim()}";
        }

        return $"{bName}{keyringSuffix}{profileSuffix}";
    }

    public async Task<(bool Success, string Message, string TechnicalDetails)> TestBrowserCookiesAsync(
        string browserSpec,
        string testUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    {
        if (string.IsNullOrWhiteSpace(browserSpec))
        {
            return (false, "No browser specified.", "Empty browser specification.");
        }

        string? binary = _ytdlpService?.DetectYtDlp();
        if (binary == null)
        {
            string defaultPath = Path.Combine(PathUtils.GetBinDir(), "yt-dlp.exe");
            if (File.Exists(defaultPath)) binary = defaultPath;
        }

        if (binary == null)
        {
            return (false, "yt-dlp engine is not available. Please ensure engine is initialized.", "Binary not found.");
        }

        try
        {
            var args = new[]
            {
                "--cookies-from-browser", browserSpec,
                "--simulate",
                "--no-warnings",
                "--no-playlist",
                testUrl
            };

            var res = await ProcessRunner.RunAsync(binary, args);
            string combined = $"{res.StandardError}\n{res.StandardOutput}".Trim();

            var match = Regex.Match(combined, @"Extracted\s+(\d+)\s+cookies", RegexOptions.IgnoreCase);
            if (res.Success || match.Success)
            {
                string count = match.Success ? $" ({match.Groups[1].Value} cookies loaded)" : string.Empty;
                return (true, $"Browser cookies successfully read from '{browserSpec}'{count}.", combined.Length > 0 ? combined : "OK");
            }

            string lower = combined.ToLowerInvariant();
            string userMsg;
            if (lower.Contains("could not copy") || lower.Contains("permission denied") || lower.Contains("permissionerror") || lower.Contains("database is locked") || lower.Contains("locked") || lower.Contains("operationalerror"))
            {
                userMsg = $"Could not access '{browserSpec}' cookies because the browser is currently running and has locked its cookie database. Please close all browser windows and background processes in Task Manager, or use a Netscape cookies.txt file.";
            }
            else if (lower.Contains("failed to decrypt with dpapi") || lower.Contains("10927") || lower.Contains("app-bound"))
            {
                userMsg = $"Microsoft Edge / Chrome security (App-Bound Encryption) prevents direct extraction by yt-dlp. To download, please export a 'cookies.txt' file using the 'Get cookies.txt LOCALLY' extension or use Firefox.";
            }
            else if (lower.Contains("could not find") || lower.Contains("profile"))
            {
                userMsg = $"Could not find the specified browser profile for '{browserSpec}'. Verify that the profile exists in your browser.";
            }
            else if (lower.Contains("keyring") || lower.Contains("decrypt"))
            {
                userMsg = $"Cookie decryption failed for '{browserSpec}'. Try choosing another browser (such as Firefox) or using a cookies.txt file.";
            }
            else
            {
                userMsg = $"yt-dlp could not extract cookies from '{browserSpec}'.";
            }

            return (false, userMsg, combined.Length > 0 ? combined : $"Exit code: {res.ExitCode}");
        }
        catch (Exception ex)
        {
            return (false, $"Failed to test browser cookies: {ex.Message}", ex.ToString());
        }
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
        if (settings.GetEffectiveCookieMethod() != "file")
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
        if (settings.GetEffectiveCookieMethod() != "file") return null;

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

    public List<string> GetCookieArguments(string? profileNameOrId = null, string? customCookieFile = null, string? browserSpec = null)
    {
        // 1. Explicit browser spec passed
        if (!string.IsNullOrWhiteSpace(browserSpec))
        {
            string cleanSpec = browserSpec.StartsWith("browser:", StringComparison.OrdinalIgnoreCase)
                ? browserSpec.Substring(8)
                : browserSpec;
            if (!string.IsNullOrWhiteSpace(cleanSpec))
            {
                return new List<string> { "--cookies-from-browser", cleanSpec };
            }
        }

        // 2. Explicit custom cookie file passed
        if (!string.IsNullOrWhiteSpace(customCookieFile))
        {
            if (customCookieFile.StartsWith("browser:", StringComparison.OrdinalIgnoreCase))
            {
                return new List<string> { "--cookies-from-browser", customCookieFile.Substring(8) };
            }
            if (ValidateCookieFile(customCookieFile))
            {
                return new List<string> { "--cookies", customCookieFile };
            }
        }

        // 3. Named profile passed
        if (!string.IsNullOrWhiteSpace(profileNameOrId))
        {
            if (profileNameOrId.StartsWith("browser:", StringComparison.OrdinalIgnoreCase))
            {
                return new List<string> { "--cookies-from-browser", profileNameOrId.Substring(8) };
            }
            var p = GetProfiles().FirstOrDefault(x => x.Id == profileNameOrId || x.Name.Equals(profileNameOrId, StringComparison.OrdinalIgnoreCase));
            if (p != null && ValidateCookieFile(p.FilePath))
            {
                return new List<string> { "--cookies", p.FilePath };
            }
        }

        // 4. Default global settings
        var settings = _settingsService.GetSettings();
        string method = settings.GetEffectiveCookieMethod();

        if (method == "browser")
        {
            string spec = BuildBrowserSpec(settings.BrowserName, settings.BrowserProfile, settings.BrowserKeyring);
            if (!string.IsNullOrWhiteSpace(spec))
            {
                return new List<string> { "--cookies-from-browser", spec };
            }
        }
        else if (method == "file")
        {
            string? active = GetActiveCookiePath();
            if (!string.IsNullOrWhiteSpace(active) && ValidateCookieFile(active))
            {
                return new List<string> { "--cookies", active };
            }
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

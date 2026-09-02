using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Glypdl.Windows.Models;
using Glypdl.Windows.Services;

namespace Glypdl.Windows.ViewModels;

public partial class SettingsViewModel : ObservableObject
{
    private readonly ISettingsService _settingsService;
    private readonly ICookieService _cookieService;
    private readonly IYtDlpService _ytdlpService;

    [ObservableProperty]
    private string _downloadDirectory = string.Empty;

    [ObservableProperty]
    private double _maxConcurrentDownloads = 2;

    [ObservableProperty]
    private bool _enableNotifications = true;

    [ObservableProperty]
    private string _filenameTemplate = "%(title)s.%(ext)s";

    [ObservableProperty]
    private string _customYtDlpPath = string.Empty;

    [ObservableProperty]
    private string _customFFmpegPath = string.Empty;

    [ObservableProperty]
    private string _extraArguments = string.Empty;

    [ObservableProperty]
    private int _cookieMethodIndex; // 0: None, 1: Browser, 2: File

    partial void OnCookieMethodIndexChanged(int value)
    {
        OnPropertyChanged(nameof(IsCookieMethodNone));
        OnPropertyChanged(nameof(IsCookieMethodBrowser));
        OnPropertyChanged(nameof(IsCookieMethodFile));
        UseCookies = value != 0;
        UpdateProfileActiveStates();
        SaveSettings();
    }

    public bool IsCookieMethodNone => CookieMethodIndex == 0;
    public bool IsCookieMethodBrowser => CookieMethodIndex == 1;
    public bool IsCookieMethodFile => CookieMethodIndex == 2;

    [ObservableProperty]
    private BrowserInfo? _selectedBrowser;

    partial void OnSelectedBrowserChanged(BrowserInfo? value)
    {
        RefreshBrowserProfiles();
        UpdateChromiumPolicyInfo();
        SaveSettings();
    }

    [ObservableProperty]
    private string? _selectedBrowserProfile = "Default";

    partial void OnSelectedBrowserProfileChanged(string? value)
    {
        SaveSettings();
    }

    [ObservableProperty]
    private KeyringInfo? _selectedKeyring;

    partial void OnSelectedKeyringChanged(KeyringInfo? value)
    {
        SaveSettings();
    }

    [ObservableProperty]
    private bool _isTestingBrowser;

    [ObservableProperty]
    private string _browserTestStatus = string.Empty;

    [ObservableProperty]
    private bool _browserTestSuccess;

    [ObservableProperty]
    private bool _showBrowserTestResult;

    [ObservableProperty]
    private string _browserTestDetails = string.Empty;

    [ObservableProperty]
    private bool _useCookies;

    [ObservableProperty]
    private string _activeCookieFile = string.Empty;

    [ObservableProperty]
    private CookieProfile? _selectedCookieProfile;

    [ObservableProperty]
    private bool _hasCookieProfiles;

    [ObservableProperty]
    private bool _hasNoCookieProfiles = true;

    [ObservableProperty]
    private string _detectedYtDlpVersion = "Checking...";

    [ObservableProperty]
    private bool _isChromiumBrowserSelected;

    [ObservableProperty]
    private string _chromiumEnablePolicyCommand = string.Empty;

    [ObservableProperty]
    private string _chromiumRestorePolicyCommand = string.Empty;

    [ObservableProperty]
    private string _selectedBrowserFriendlyName = string.Empty;

    [ObservableProperty]
    private string _copyStatusMessage = string.Empty;

    public ObservableCollection<BrowserInfo> DiscoveredBrowsers { get; } = new();
    public ObservableCollection<string> BrowserProfiles { get; } = new();
    public ObservableCollection<KeyringInfo> SupportedKeyrings { get; } = new();
    public ObservableCollection<CookieProfile> CookieProfiles { get; } = new();

    public SettingsViewModel(
        ISettingsService settingsService,
        ICookieService cookieService,
        IYtDlpService ytdlpService)
    {
        _settingsService = settingsService;
        _cookieService = cookieService;
        _ytdlpService = ytdlpService;
        LoadSettings();
    }

    public void LoadSettings()
    {
        var s = _settingsService.GetSettings();
        DownloadDirectory = s.DownloadDirectory;
        MaxConcurrentDownloads = s.MaxConcurrentDownloads;
        EnableNotifications = s.EnableNotifications;
        FilenameTemplate = s.FilenameTemplate;
        CustomYtDlpPath = s.CustomYtDlpPath;
        CustomFFmpegPath = s.CustomFFmpegPath;
        ExtraArguments = s.ExtraArguments;
        UseCookies = s.UseCookies;
        ActiveCookieFile = s.ActiveCookieFile;

        string effectiveMethod = s.GetEffectiveCookieMethod();
        CookieMethodIndex = effectiveMethod switch
        {
            "browser" => 1,
            "file" => 2,
            _ => 0
        };

        // Initialize keyrings
        SupportedKeyrings.Clear();
        foreach (var k in _cookieService.GetSupportedKeyrings())
        {
            SupportedKeyrings.Add(k);
        }
        SelectedKeyring = SupportedKeyrings.FirstOrDefault(k => k.Id.Equals(s.BrowserKeyring, StringComparison.OrdinalIgnoreCase))
            ?? SupportedKeyrings.FirstOrDefault();

        // Initialize browsers
        RefreshDiscoveredBrowsers(s.BrowserName, s.BrowserProfile);

        RefreshProfiles();

        if (!string.IsNullOrWhiteSpace(s.ActiveCookieProfileId))
        {
            SelectedCookieProfile = CookieProfiles.FirstOrDefault(p => p.Id == s.ActiveCookieProfileId);
        }
        if (SelectedCookieProfile != null && string.IsNullOrWhiteSpace(ActiveCookieFile))
        {
            ActiveCookieFile = SelectedCookieProfile.FilePath;
        }

        UpdateChromiumPolicyInfo();
        UpdateProfileActiveStates();
        _ = RefreshVersionAsync();
    }

    public void RefreshDiscoveredBrowsers(string? targetBrowser = null, string? targetProfile = null)
    {
        DiscoveredBrowsers.Clear();
        var list = _cookieService.DiscoverInstalledBrowsers(forceRefresh: true);
        foreach (var b in list)
        {
            DiscoveredBrowsers.Add(b);
        }

        if (!string.IsNullOrWhiteSpace(targetBrowser))
        {
            SelectedBrowser = DiscoveredBrowsers.FirstOrDefault(b => b.Id.Equals(targetBrowser, StringComparison.OrdinalIgnoreCase))
                ?? DiscoveredBrowsers.FirstOrDefault(b => b.IsInstalled)
                ?? DiscoveredBrowsers.FirstOrDefault();
        }
        else
        {
            SelectedBrowser = DiscoveredBrowsers.FirstOrDefault(b => b.IsInstalled) ?? DiscoveredBrowsers.FirstOrDefault();
        }

        RefreshBrowserProfiles(targetProfile);
    }

    private void RefreshBrowserProfiles(string? targetProfile = null)
    {
        BrowserProfiles.Clear();
        if (SelectedBrowser != null && SelectedBrowser.Profiles.Count > 0)
        {
            foreach (var p in SelectedBrowser.Profiles)
            {
                BrowserProfiles.Add(p);
            }
        }
        else
        {
            BrowserProfiles.Add("Default");
        }

        if (!string.IsNullOrWhiteSpace(targetProfile) && BrowserProfiles.Contains(targetProfile))
        {
            SelectedBrowserProfile = targetProfile;
        }
        else
        {
            SelectedBrowserProfile = BrowserProfiles.FirstOrDefault() ?? "Default";
        }
    }

    [RelayCommand]
    public async Task TestBrowserCookiesAsync()
    {
        if (SelectedBrowser == null) return;

        IsTestingBrowser = true;
        ShowBrowserTestResult = false;
        BrowserTestStatus = "Testing browser cookie extraction...";

        string spec = _cookieService.BuildBrowserSpec(
            SelectedBrowser.Id,
            SelectedBrowserProfile,
            SelectedKeyring?.Id);

        var (success, msg, details) = await _cookieService.TestBrowserCookiesAsync(spec);

        Utilities.DispatcherHelper.ExecuteOnUIThread(() =>
        {
            IsTestingBrowser = false;
            BrowserTestSuccess = success;
            BrowserTestStatus = msg;
            BrowserTestDetails = details;
            ShowBrowserTestResult = true;
        });
    }

    [RelayCommand]
    public void RefreshBrowsers()
    {
        RefreshDiscoveredBrowsers(SelectedBrowser?.Id, SelectedBrowserProfile);
        UpdateChromiumPolicyInfo();
    }

    public void UpdateChromiumPolicyInfo()
    {
        string? id = SelectedBrowser?.Id?.ToLowerInvariant();
        if (id == "firefox" || id == "librewolf" || string.IsNullOrWhiteSpace(id))
        {
            IsChromiumBrowserSelected = false;
            ChromiumEnablePolicyCommand = string.Empty;
            ChromiumRestorePolicyCommand = string.Empty;
            SelectedBrowserFriendlyName = SelectedBrowser?.Name ?? "Browser";
            return;
        }

        IsChromiumBrowserSelected = true;
        SelectedBrowserFriendlyName = SelectedBrowser?.Name ?? "Chromium Browser";

        string policySubKey = id switch
        {
            "edge" => @"Microsoft\Edge",
            "chrome" => @"Google\Chrome",
            "brave" => @"BraveSoftware\Brave",
            "chromium" => @"Chromium",
            "vivaldi" => @"Vivaldi",
            "opera" => @"Opera Software",
            _ => @"Google\Chrome"
        };

        ChromiumEnablePolicyCommand = $"reg add \"HKLM\\SOFTWARE\\Policies\\{policySubKey}\" /v ApplicationBoundEncryptionEnabled /t REG_DWORD /d 0 /f";
        ChromiumRestorePolicyCommand = $"reg delete \"HKLM\\SOFTWARE\\Policies\\{policySubKey}\" /v ApplicationBoundEncryptionEnabled /f";
    }

    [RelayCommand]
    public async Task CopyEnableAsync()
    {
        if (string.IsNullOrWhiteSpace(ChromiumEnablePolicyCommand)) return;
        try
        {
            var dataPackage = new global::Windows.ApplicationModel.DataTransfer.DataPackage();
            dataPackage.SetText(ChromiumEnablePolicyCommand);
            global::Windows.ApplicationModel.DataTransfer.Clipboard.SetContent(dataPackage);
            CopyStatusMessage = $"Command for {SelectedBrowserFriendlyName} copied! ✅";
            await Task.Delay(3000);
            CopyStatusMessage = string.Empty;
        }
        catch { }
    }

    [RelayCommand]
    public async Task CopyRestoreAsync()
    {
        if (string.IsNullOrWhiteSpace(ChromiumRestorePolicyCommand)) return;
        try
        {
            var dataPackage = new global::Windows.ApplicationModel.DataTransfer.DataPackage();
            dataPackage.SetText(ChromiumRestorePolicyCommand);
            global::Windows.ApplicationModel.DataTransfer.Clipboard.SetContent(dataPackage);
            CopyStatusMessage = $"Restore command for {SelectedBrowserFriendlyName} copied! ✅";
            await Task.Delay(3000);
            CopyStatusMessage = string.Empty;
        }
        catch { }
    }

    [ObservableProperty]
    private string _saveStatusMessage = string.Empty;

    [RelayCommand]
    public async Task SaveSettingsAsync()
    {
        SaveSettings();
        Utilities.DispatcherHelper.ExecuteOnUIThread(() =>
        {
            SaveStatusMessage = "Preferences saved successfully! ✅";
        });
        await Task.Delay(3500);
        Utilities.DispatcherHelper.ExecuteOnUIThread(() =>
        {
            SaveStatusMessage = string.Empty;
        });
    }

    public void SaveSettings()
    {
        string method = CookieMethodIndex switch
        {
            1 => "browser",
            2 => "file",
            _ => "none"
        };

        var s = new AppSettings
        {
            DownloadDirectory = DownloadDirectory,
            MaxConcurrentDownloads = (int)MaxConcurrentDownloads,
            EnableNotifications = EnableNotifications,
            Theme = AppTheme.System,
            FilenameTemplate = FilenameTemplate,
            CustomYtDlpPath = CustomYtDlpPath,
            CustomFFmpegPath = CustomFFmpegPath,
            ExtraArguments = ExtraArguments,
            UseCookies = CookieMethodIndex != 0,
            CookieMethod = method,
            ActiveCookieProfileId = SelectedCookieProfile?.Id ?? string.Empty,
            ActiveCookieFile = ActiveCookieFile,
            BrowserName = SelectedBrowser?.Id ?? "edge",
            BrowserProfile = SelectedBrowserProfile ?? "Default",
            BrowserKeyring = SelectedKeyring?.Id ?? "auto"
        };

        _settingsService.SaveSettings(s);
    }

    [RelayCommand]
    public void UseProfile(CookieProfile profile)
    {
        if (profile == null) return;
        SelectedCookieProfile = profile;
        ActiveCookieFile = profile.FilePath;
        UseCookies = true;
        UpdateProfileActiveStates();
        SaveSettings();
    }

    [RelayCommand]
    public void ClearActiveCookie()
    {
        ActiveCookieFile = string.Empty;
        SelectedCookieProfile = null;
        UpdateProfileActiveStates();
        SaveSettings();
    }

    public void SetActiveCookieFile(string path)
    {
        ActiveCookieFile = path;
        var matchingProfile = CookieProfiles.FirstOrDefault(p => p.FilePath == path);
        SelectedCookieProfile = matchingProfile;
        UseCookies = true;
        UpdateProfileActiveStates();
        SaveSettings();
    }

    public void AddCookieProfile(string name, string filePath)
    {
        _cookieService.AddProfile(name, filePath);
        RefreshProfiles();
        SaveSettings();
    }

    [RelayCommand]
    public void RemoveCookieProfile(CookieProfile profile)
    {
        if (profile == null) return;
        if (SelectedCookieProfile?.Id == profile.Id || ActiveCookieFile == profile.FilePath)
        {
            SelectedCookieProfile = null;
            ActiveCookieFile = string.Empty;
        }
        _cookieService.RemoveProfile(profile.Id);
        CookieProfiles.Remove(profile);
        UpdateProfileActiveStates();
        SaveSettings();
    }

    public void RefreshProfiles()
    {
        CookieProfiles.Clear();
        foreach (var p in _cookieService.GetProfiles())
        {
            CookieProfiles.Add(p);
        }
        UpdateProfileActiveStates();
    }

    private void UpdateProfileActiveStates()
    {
        HasCookieProfiles = CookieProfiles.Count > 0;
        HasNoCookieProfiles = CookieProfiles.Count == 0;

        foreach (var p in CookieProfiles)
        {
            p.IsActive = UseCookies && (
                (!string.IsNullOrWhiteSpace(ActiveCookieFile) && p.FilePath == ActiveCookieFile) ||
                (SelectedCookieProfile != null && p.Id == SelectedCookieProfile.Id)
            );
        }
    }

    private async Task RefreshVersionAsync()
    {
        string? v = await _ytdlpService.GetVersionAsync();
        DetectedYtDlpVersion = v ?? "Not found (yt-dlp.exe required)";
    }
}

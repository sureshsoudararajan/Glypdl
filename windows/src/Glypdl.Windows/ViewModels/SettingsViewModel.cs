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
    private bool _useCookies;

    partial void OnUseCookiesChanged(bool value)
    {
        UpdateProfileActiveStates();
        SaveSettings();
    }

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

        RefreshProfiles();

        if (!string.IsNullOrWhiteSpace(s.ActiveCookieProfileId))
        {
            SelectedCookieProfile = CookieProfiles.FirstOrDefault(p => p.Id == s.ActiveCookieProfileId);
        }
        if (SelectedCookieProfile != null && string.IsNullOrWhiteSpace(ActiveCookieFile))
        {
            ActiveCookieFile = SelectedCookieProfile.FilePath;
        }

        UpdateProfileActiveStates();
        _ = RefreshVersionAsync();
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
            UseCookies = UseCookies,
            ActiveCookieProfileId = SelectedCookieProfile?.Id ?? string.Empty,
            ActiveCookieFile = ActiveCookieFile
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

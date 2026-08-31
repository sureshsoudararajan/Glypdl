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
    private AppTheme _selectedTheme = AppTheme.System;

    partial void OnSelectedThemeChanged(AppTheme value)
    {
        App.ApplyTheme(value);
        OnPropertyChanged(nameof(ThemeIndex));
        SaveSettings();
    }

    public int ThemeIndex
    {
        get => (int)SelectedTheme;
        set
        {
            if ((int)SelectedTheme != value && value >= 0)
            {
                SelectedTheme = (AppTheme)value;
            }
        }
    }

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

    [ObservableProperty]
    private CookieProfile? _selectedCookieProfile;

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

    private void LoadSettings()
    {
        var s = _settingsService.GetSettings();
        DownloadDirectory = s.DownloadDirectory;
        MaxConcurrentDownloads = s.MaxConcurrentDownloads;
        EnableNotifications = s.EnableNotifications;
        SelectedTheme = s.Theme;
        FilenameTemplate = s.FilenameTemplate;
        CustomYtDlpPath = s.CustomYtDlpPath;
        CustomFFmpegPath = s.CustomFFmpegPath;
        ExtraArguments = s.ExtraArguments;
        UseCookies = s.UseCookies;

        CookieProfiles.Clear();
        foreach (var p in _cookieService.GetProfiles())
        {
            CookieProfiles.Add(p);
        }

        if (!string.IsNullOrWhiteSpace(s.ActiveCookieProfileId))
        {
            SelectedCookieProfile = CookieProfiles.FirstOrDefault(p => p.Id == s.ActiveCookieProfileId);
        }

        _ = RefreshVersionAsync();
    }

    [RelayCommand]
    public void SaveSettings()
    {
        var s = new AppSettings
        {
            DownloadDirectory = DownloadDirectory,
            MaxConcurrentDownloads = (int)MaxConcurrentDownloads,
            EnableNotifications = EnableNotifications,
            Theme = SelectedTheme,
            FilenameTemplate = FilenameTemplate,
            CustomYtDlpPath = CustomYtDlpPath,
            CustomFFmpegPath = CustomFFmpegPath,
            ExtraArguments = ExtraArguments,
            UseCookies = UseCookies,
            ActiveCookieProfileId = SelectedCookieProfile?.Id ?? string.Empty
        };

        _settingsService.SaveSettings(s);
    }

    public void AddCookieProfile(string name, string filePath)
    {
        _cookieService.AddProfile(name, filePath);
        CookieProfiles.Clear();
        foreach (var p in _cookieService.GetProfiles())
        {
            CookieProfiles.Add(p);
        }
    }

    [RelayCommand]
    public void RemoveCookieProfile(CookieProfile profile)
    {
        _cookieService.RemoveProfile(profile.Id);
        CookieProfiles.Remove(profile);
    }

    private async Task RefreshVersionAsync()
    {
        string? v = await _ytdlpService.GetVersionAsync();
        DetectedYtDlpVersion = v ?? "Not found (yt-dlp.exe required)";
    }
}

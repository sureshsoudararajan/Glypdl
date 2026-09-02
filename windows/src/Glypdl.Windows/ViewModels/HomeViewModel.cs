using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Glypdl.Windows.Models;
using Glypdl.Windows.Services;

namespace Glypdl.Windows.ViewModels;

public partial class HomeViewModel : ObservableObject
{
    private readonly IMetadataService _metadataService;
    private readonly IQueueService _queueService;
    private readonly ISettingsService _settingsService;
    private readonly ICookieService _cookieService;
    private readonly IHistoryService _historyService;

    [ObservableProperty]
    private string _urlInput = string.Empty;

    [ObservableProperty]
    private bool _isLoading;

    [ObservableProperty]
    private string _statusMessage = string.Empty;

    [ObservableProperty]
    private string _alreadyDownloadedInfo = string.Empty;

    [ObservableProperty]
    private MediaMetadata? _previewMetadata;

    [ObservableProperty]
    private bool _isPlaylist;

    [ObservableProperty]
    private int _selectedPlaylistCount;

    [ObservableProperty]
    private int _totalPlaylistCount;

    [ObservableProperty]
    private string _playlistSelectionSummary = string.Empty;

    [ObservableProperty]
    private string _downloadButtonText = "Start Download";

    public ObservableCollection<PlaylistItem> PlaylistItems { get; } = new();

    [ObservableProperty]
    private DownloadMode _selectedMode = DownloadMode.VideoAudio;

    public int SelectedModeIndex
    {
        get => (int)SelectedMode;
        set
        {
            if ((int)SelectedMode != value && value >= 0)
            {
                SelectedMode = (DownloadMode)value;
                OnPropertyChanged(nameof(SelectedModeIndex));
                OnPropertyChanged(nameof(IsAudioOnly));
                OnPropertyChanged(nameof(IsVideoMode));
            }
        }
    }

    public bool IsAudioOnly => SelectedMode == DownloadMode.AudioOnly;
    public bool IsVideoMode => SelectedMode != DownloadMode.AudioOnly;

    [ObservableProperty]
    private string _selectedQuality = "Best";

    [ObservableProperty]
    private string _selectedAudioFormat = "webm";

    [ObservableProperty]
    private string _selectedAudioBitrate = "320 kbps (Best)";

    [ObservableProperty]
    private List<string> _availableQualities = new() { "Best" };

    public List<string> AudioFormats { get; } = new() { "webm", "mp3", "m4a", "opus" };
    public List<string> AudioBitrates { get; } = new() { "320 kbps (Best)", "256 kbps (High)", "192 kbps (Medium)", "128 kbps (Standard)", "96 kbps (Low)" };

    public ObservableCollection<CookieOptionItem> CookieOptions { get; } = new();

    [ObservableProperty]
    private CookieOptionItem? _selectedCookieOption;

    [ObservableProperty]
    private bool _hasCookieOptions;

    [ObservableProperty]
    private bool _isAuthWarningVisible;

    [ObservableProperty]
    private string _authWarningTitle = "Authentication Cookies Required";

    [ObservableProperty]
    private string _authWarningMessage = string.Empty;

    [ObservableProperty]
    private bool _isCookiesDisabled;

    public event Func<string, string, Task>? RequestAuthRecovery;

    [RelayCommand]
    public void GoToSettings()
    {
        App.NavigateToSettings();
    }

    [RelayCommand]
    public void DismissAuthWarning()
    {
        IsAuthWarningVisible = false;
    }

    public HomeViewModel(
        IMetadataService metadataService,
        IQueueService queueService,
        ISettingsService settingsService,
        ICookieService cookieService,
        IHistoryService historyService)
    {
        _metadataService = metadataService;
        _queueService = queueService;
        _settingsService = settingsService;
        _cookieService = cookieService;
        _historyService = historyService;
    }

    public void PopulateCookieOptions(string? activeCookiePath = null)
    {
        CookieOptions.Clear();
        var defaultOption = new CookieOptionItem
        {
            Type = "none",
            DisplayName = "None (Anonymous)",
            FilePath = string.Empty,
            Spec = string.Empty,
            Description = "No authentication cookies attached"
        };
        CookieOptions.Add(defaultOption);

        var settings = _settingsService.GetSettings();

        // 1. Add Discovered Installed Browsers
        var installedBrowsers = _cookieService.DiscoverInstalledBrowsers().Where(b => b.IsInstalled).ToList();
        foreach (var b in installedBrowsers)
        {
            foreach (var prof in b.Profiles)
            {
                string spec = _cookieService.BuildBrowserSpec(b.Id, prof, settings.BrowserKeyring);
                string label = prof == "Default" ? $"🌐 {b.Name}" : $"🌐 {b.Name} ({prof})";
                CookieOptions.Add(new CookieOptionItem
                {
                    Type = "browser",
                    DisplayName = label,
                    Spec = $"browser:{spec}",
                    FilePath = string.Empty,
                    Description = $"Extract session cookies from {b.Name}"
                });
            }
        }

        // 2. Add Saved Netscape Cookie Files & Profiles
        var seenPaths = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var profiles = _cookieService.GetProfiles();
        foreach (var p in profiles)
        {
            if (!string.IsNullOrWhiteSpace(p.FilePath) && File.Exists(p.FilePath))
            {
                string baseName = System.IO.Path.GetFileName(p.FilePath);
                CookieOptions.Add(new CookieOptionItem
                {
                    Type = "file",
                    DisplayName = $"📁 {p.Name} ({baseName})",
                    FilePath = p.FilePath,
                    Spec = string.Empty,
                    Description = $"Netscape cookie file: {p.FilePath}"
                });
                seenPaths.Add(p.FilePath);
            }
        }

        if (!string.IsNullOrWhiteSpace(settings.ActiveCookieFile) && File.Exists(settings.ActiveCookieFile) && !seenPaths.Contains(settings.ActiveCookieFile))
        {
            string baseName = System.IO.Path.GetFileName(settings.ActiveCookieFile);
            CookieOptions.Add(new CookieOptionItem
            {
                Type = "file",
                DisplayName = $"📁 Default Cookie ({baseName})",
                FilePath = settings.ActiveCookieFile,
                Spec = string.Empty,
                Description = $"Active cookie file: {settings.ActiveCookieFile}"
            });
            seenPaths.Add(settings.ActiveCookieFile);
        }

        HasCookieOptions = CookieOptions.Count > 1 || settings.UseCookies || !string.IsNullOrWhiteSpace(activeCookiePath);

        CookieOptionItem? chosen = null;
        if (!string.IsNullOrWhiteSpace(activeCookiePath))
        {
            if (activeCookiePath.StartsWith("browser:", StringComparison.OrdinalIgnoreCase))
            {
                chosen = CookieOptions.FirstOrDefault(c => string.Equals(c.Spec, activeCookiePath, StringComparison.OrdinalIgnoreCase));
            }
            else
            {
                chosen = CookieOptions.FirstOrDefault(c => string.Equals(c.FilePath, activeCookiePath, StringComparison.OrdinalIgnoreCase));
            }
        }

        if (chosen == null && settings.UseCookies)
        {
            string method = settings.GetEffectiveCookieMethod();
            if (method == "browser")
            {
                string spec = $"browser:{_cookieService.BuildBrowserSpec(settings.BrowserName, settings.BrowserProfile, settings.BrowserKeyring)}";
                chosen = CookieOptions.FirstOrDefault(c => string.Equals(c.Spec, spec, StringComparison.OrdinalIgnoreCase));
            }
            else if (method == "file")
            {
                string? active = _cookieService.GetActiveCookiePath();
                if (!string.IsNullOrWhiteSpace(active))
                {
                    chosen = CookieOptions.FirstOrDefault(c => string.Equals(c.FilePath, active, StringComparison.OrdinalIgnoreCase));
                }
            }
        }

        SelectedCookieOption = chosen ?? defaultOption;
    }

    public static bool IsAuthError(string? errorMsg, string? url = null)
    {
        if (string.IsNullOrWhiteSpace(errorMsg)) return false;

        string lower = errorMsg.ToLowerInvariant();

        // If it explicitly says no video formats found, it's an image-only post / non-video post, not an auth error
        if (lower.Contains("no video formats found") || lower.Contains("no video format"))
        {
            return false;
        }

        string[] authKeywords = {
            "sign in", "login", "cookie", "bot", "confirm", "private", "members", "403", "forbidden",
            "authenticate", "permission", "unauthorized", "account", "checkpoint", "rate-limit",
            "dpapi", "10927", "7271", "app-bound", "could not copy", "permission denied", "permissionerror",
            "database is locked", "locked", "operationalerror", "extract_chrome_cookies", "extract_cookies_from_browser"
        };
        return authKeywords.Any(k => lower.Contains(k));
    }

    [RelayCommand]
    public async Task FetchMetadataAsync()
    {
        string? activeCookie = null;
        var settings = _settingsService.GetSettings();
        if (settings.UseCookies)
        {
            string method = settings.GetEffectiveCookieMethod();
            if (method == "browser")
            {
                string spec = _cookieService.BuildBrowserSpec(settings.BrowserName, settings.BrowserProfile, settings.BrowserKeyring);
                if (!string.IsNullOrWhiteSpace(spec))
                {
                    activeCookie = $"browser:{spec}";
                }
            }
            else if (method == "file")
            {
                activeCookie = _cookieService.GetActiveCookiePath();
            }
        }
        await FetchMetadataInternalAsync(UrlInput?.Trim() ?? string.Empty, activeCookie);
    }

    public async Task FetchMetadataWithCookieAsync(string url, string? cookiePath)
    {
        UrlInput = url;
        await FetchMetadataInternalAsync(url, cookiePath);
    }

    private async Task FetchMetadataInternalAsync(string url, string? cookiePath)
    {
        if (string.IsNullOrWhiteSpace(url)) return;

        Utilities.DispatcherHelper.ExecuteOnUIThread(() =>
        {
            IsLoading = true;
            StatusMessage = "Fetching video details...";
            AlreadyDownloadedInfo = string.Empty;
            PreviewMetadata = null;
            PlaylistItems.Clear();
            IsPlaylist = false;
            IsAuthWarningVisible = false;
        });

        try
        {
            var meta = await _metadataService.FetchMetadataAsync(url, cookiePath);

            if (meta != null)
            {
                if (!string.IsNullOrWhiteSpace(meta.ThumbnailUrl) && (meta.ThumbnailUrl.StartsWith("http://", StringComparison.OrdinalIgnoreCase) || meta.ThumbnailUrl.StartsWith("https://", StringComparison.OrdinalIgnoreCase)))
                {
                    try
                    {
                        var thumbDir = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Glypdl", "thumbnails");
                        System.IO.Directory.CreateDirectory(thumbDir);
                        var cleanId = Guid.NewGuid().ToString("N");
                        var filePath = System.IO.Path.Combine(thumbDir, $"{cleanId}.jpg");
                        using var http = new HttpClient();
                        var bytes = await http.GetByteArrayAsync(meta.ThumbnailUrl);
                        await System.IO.File.WriteAllBytesAsync(filePath, bytes);
                        meta.ThumbnailUrl = filePath;
                    }
                    catch { }
                }

                string existingInfo = string.Empty;
                try
                {
                    var history = await _historyService.GetAllAsync();
                    var existing = history.FirstOrDefault(h => 
                        (!string.IsNullOrWhiteSpace(h.Url) && h.Url.Equals(url, StringComparison.OrdinalIgnoreCase)) ||
                        (!string.IsNullOrWhiteSpace(meta.Id) && !string.IsNullOrWhiteSpace(h.Url) && h.Url.Contains(meta.Id)));
                    
                    if (existing != null)
                    {
                        existingInfo = $"Note: Previously downloaded on {existing.Timestamp.ToLocalTime():MMM dd, yyyy} ({existing.Mode}, {existing.Quality})";
                    }
                }
                catch { }

                var qList = new List<string> { "Best" };
                qList.AddRange(meta.AvailableQualities);

                Utilities.DispatcherHelper.ExecuteOnUIThread(() =>
                {
                    PreviewMetadata = meta;
                    IsPlaylist = meta.IsPlaylist;
                    AlreadyDownloadedInfo = existingInfo;
                    AvailableQualities = qList;
                    SelectedQuality = "Best";
                    StatusMessage = string.Empty;
                    IsAuthWarningVisible = false;
                    PopulateCookieOptions(meta.UsedCookieFile);

                    if (meta.IsPlaylist)
                    {
                        PlaylistItems.Clear();
                        foreach (var entry in meta.PlaylistEntries)
                        {
                            entry.PropertyChanged += (s, e) =>
                            {
                                if (e.PropertyName == nameof(PlaylistItem.IsSelected))
                                {
                                    UpdatePlaylistStats();
                                }
                            };
                            PlaylistItems.Add(entry);
                        }
                        UpdatePlaylistStats();
                    }
                    else
                    {
                        DownloadButtonText = "Start Download";
                    }
                });
            }
            else
            {
                Utilities.DispatcherHelper.ExecuteOnUIThread(() =>
                {
                    StatusMessage = "No media information found for this URL.";
                });
            }
        }
        catch (Exception ex)
        {
            bool isNoVideo = ex.Message.Contains("No video formats found", StringComparison.OrdinalIgnoreCase) ||
                             ex.Message.Contains("no video format", StringComparison.OrdinalIgnoreCase);
            bool isAuth = !isNoVideo && IsAuthError(ex.Message, url);
            bool cookiesDisabled = !_settingsService.GetSettings().UseCookies;

            Utilities.DispatcherHelper.ExecuteOnUIThread(() =>
            {
                if (isNoVideo)
                {
                    IsAuthWarningVisible = false;
                    StatusMessage = "No video found: This post contains only static photos/images. Glypdl downloads video and audio streams (Reels, Videos, Stories, IGTV).";
                }
                else if (isAuth)
                {
                    IsCookiesDisabled = cookiesDisabled;
                    IsAuthWarningVisible = true;
                    AuthWarningTitle = "Authentication / Cookies Required";

                    string lowerErr = ex.Message.ToLowerInvariant();
                    if (lowerErr.Contains("dpapi") || lowerErr.Contains("10927") || lowerErr.Contains("app-bound"))
                    {
                        AuthWarningMessage = "Due to Chromium App-Bound Encryption on Windows, cookies cannot be directly extracted from Edge/Chrome. Please use Firefox or export a cookies.txt file with a browser extension and select it below to retry.";
                        StatusMessage = string.Empty;
                    }
                    else if (lowerErr.Contains("could not copy") || lowerErr.Contains("permission denied") || lowerErr.Contains("permissionerror") || lowerErr.Contains("database is locked") || lowerErr.Contains("locked"))
                    {
                        AuthWarningMessage = "Your browser is currently running and has locked its cookie file. Please close your web browser completely (including background tasks) or select/import a Netscape cookies.txt file.";
                        StatusMessage = string.Empty;
                    }
                    else if (cookiesDisabled)
                    {
                        AuthWarningMessage = "This site requires authentication cookies to access media. Cookies are currently turned OFF. Please enable Cookies in Settings or select/import a cookies.txt file to download.";
                        StatusMessage = string.Empty;
                    }
                    else
                    {
                        AuthWarningMessage = "This site requires login credentials or an updated cookie file. Please select or import a cookie profile.";
                        StatusMessage = string.Empty;
                    }
                }
                else
                {
                    IsAuthWarningVisible = false;
                    StatusMessage = $"Error: {ex.Message}";
                }
            });

            if (isAuth && RequestAuthRecovery != null)
            {
                Utilities.DispatcherHelper.ExecuteOnUIThread(async () =>
                {
                    try
                    {
                        await RequestAuthRecovery.Invoke(ex.Message, url);
                    }
                    catch { }
                });
            }
        }
        finally
        {
            Utilities.DispatcherHelper.ExecuteOnUIThread(() =>
            {
                IsLoading = false;
            });
        }
    }

    public void UpdatePlaylistStats()
    {
        Utilities.DispatcherHelper.ExecuteOnUIThread(() =>
        {
            TotalPlaylistCount = PlaylistItems.Count;
            SelectedPlaylistCount = PlaylistItems.Count(i => i.IsSelected);
            PlaylistSelectionSummary = $"{SelectedPlaylistCount} of {TotalPlaylistCount} items selected";
            DownloadButtonText = SelectedPlaylistCount > 0 
                ? $"Download {SelectedPlaylistCount} Selected {(SelectedPlaylistCount == 1 ? "Video" : "Videos")}" 
                : "Select at least 1 video";
        });
    }

    [RelayCommand]
    public void SelectAllPlaylist()
    {
        foreach (var entry in PlaylistItems)
        {
            entry.IsSelected = true;
        }
        UpdatePlaylistStats();
    }

    [RelayCommand]
    public void DeselectAllPlaylist()
    {
        foreach (var entry in PlaylistItems)
        {
            entry.IsSelected = false;
        }
        UpdatePlaylistStats();
    }

    [RelayCommand]
    public void InvertPlaylistSelection()
    {
        foreach (var entry in PlaylistItems)
        {
            entry.IsSelected = !entry.IsSelected;
        }
        UpdatePlaylistStats();
    }

    [RelayCommand]
    public void StartDownload()
    {
        if (PreviewMetadata == null) return;

        string cookie = string.Empty;
        if (SelectedCookieOption != null)
        {
            if (SelectedCookieOption.Type == "browser" && !string.IsNullOrWhiteSpace(SelectedCookieOption.Spec))
            {
                cookie = SelectedCookieOption.Spec;
            }
            else if (SelectedCookieOption.Type == "file" && !string.IsNullOrWhiteSpace(SelectedCookieOption.FilePath))
            {
                cookie = SelectedCookieOption.FilePath;
            }
        }

        if (string.IsNullOrWhiteSpace(cookie) && _settingsService.GetSettings().UseCookies)
        {
            var s = _settingsService.GetSettings();
            string method = s.GetEffectiveCookieMethod();
            if (method == "browser")
            {
                string spec = _cookieService.BuildBrowserSpec(s.BrowserName, s.BrowserProfile, s.BrowserKeyring);
                if (!string.IsNullOrWhiteSpace(spec))
                {
                    cookie = $"browser:{spec}";
                }
            }
            else if (method == "file")
            {
                cookie = _cookieService.GetActiveCookiePath() ?? string.Empty;
            }
        }

        string formatSpec = _metadataService.GetFormatSpec(PreviewMetadata, SelectedQuality, SelectedMode);

        if (IsPlaylist)
        {
            var selected = PlaylistItems.Where(e => e.IsSelected).ToList();
            if (selected.Count == 0)
            {
                StatusMessage = "Please select at least one item to download.";
                return;
            }

            foreach (var entry in selected)
            {
                var item = new DownloadItem
                {
                    Url = entry.Url,
                    Title = entry.Title,
                    Uploader = entry.Uploader,
                    Duration = entry.Duration,
                    ThumbnailUrl = entry.ThumbnailUrl,
                    Mode = SelectedMode,
                    Quality = SelectedMode == DownloadMode.AudioOnly ? SelectedAudioBitrate : SelectedQuality,
                    AudioFormat = SelectedAudioFormat,
                    FormatId = formatSpec,
                    CookieFilePath = cookie ?? string.Empty
                };
                _queueService.Enqueue(item);
            }
        }
        else
        {
            var item = new DownloadItem
            {
                Url = PreviewMetadata.Url,
                Title = PreviewMetadata.Title,
                Uploader = PreviewMetadata.Uploader,
                Duration = PreviewMetadata.Duration,
                ThumbnailUrl = PreviewMetadata.ThumbnailUrl,
                Mode = SelectedMode,
                Quality = SelectedMode == DownloadMode.AudioOnly ? SelectedAudioBitrate : SelectedQuality,
                AudioFormat = SelectedAudioFormat,
                FormatId = formatSpec,
                CookieFilePath = cookie ?? string.Empty
            };
            _queueService.Enqueue(item);
        }

        UrlInput = string.Empty;
        PreviewMetadata = null;
        PlaylistItems.Clear();
        IsPlaylist = false;
        StatusMessage = string.Empty;
        AlreadyDownloadedInfo = string.Empty;
        App.NavigateToDownloads();
    }

    [RelayCommand]
    public void ToggleSelectAllPlaylist(bool selectAll)
    {
        if (selectAll) SelectAllPlaylist();
        else DeselectAllPlaylist();
    }
}

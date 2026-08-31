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
    private string _selectedAudioFormat = "mp3";

    [ObservableProperty]
    private string _selectedAudioBitrate = "320 kbps (Best)";

    [ObservableProperty]
    private List<string> _availableQualities = new() { "Best" };

    public List<string> AudioFormats { get; } = new() { "mp3", "m4a", "flac", "opus", "wav", "aac" };
    public List<string> AudioBitrates { get; } = new() { "320 kbps (Best)", "256 kbps (High)", "192 kbps (Medium)", "128 kbps (Standard)", "96 kbps (Low)" };

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

    [RelayCommand]
    public async Task FetchMetadataAsync()
    {
        if (string.IsNullOrWhiteSpace(UrlInput)) return;

        IsLoading = true;
        StatusMessage = "Fetching video details...";
        AlreadyDownloadedInfo = string.Empty;
        PreviewMetadata = null;
        Utilities.DispatcherHelper.ExecuteOnUIThread(() =>
        {
            PlaylistItems.Clear();
            IsPlaylist = false;
        });

        try
        {
            var cookie = _cookieService.GetActiveProfile()?.FilePath;
            var meta = await _metadataService.FetchMetadataAsync(UrlInput.Trim(), cookie);
            PreviewMetadata = meta;

            if (meta != null)
            {
                Utilities.DispatcherHelper.ExecuteOnUIThread(() =>
                {
                    IsPlaylist = meta.IsPlaylist;
                });

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

                if (meta.IsPlaylist)
                {
                    Utilities.DispatcherHelper.ExecuteOnUIThread(() =>
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
                    });
                }
                else
                {
                    Utilities.DispatcherHelper.ExecuteOnUIThread(() =>
                    {
                        DownloadButtonText = "Start Download";
                    });
                }

                var qList = new List<string> { "Best" };
                qList.AddRange(meta.AvailableQualities);
                Utilities.DispatcherHelper.ExecuteOnUIThread(() =>
                {
                    AvailableQualities = qList;
                    SelectedQuality = "Best";
                    StatusMessage = string.Empty;
                });

                try
                {
                    var history = await _historyService.GetAllAsync();
                    var existing = history.FirstOrDefault(h => 
                        (!string.IsNullOrWhiteSpace(h.Url) && h.Url.Equals(UrlInput.Trim(), StringComparison.OrdinalIgnoreCase)) ||
                        (!string.IsNullOrWhiteSpace(meta.Id) && !string.IsNullOrWhiteSpace(h.Url) && h.Url.Contains(meta.Id)));
                    
                    if (existing != null)
                    {
                        AlreadyDownloadedInfo = $"Note: Previously downloaded on {existing.Timestamp.ToLocalTime():MMM dd, yyyy} ({existing.Mode}, {existing.Quality})";
                    }
                }
                catch { }
            }
        }
        catch (Exception ex)
        {
            StatusMessage = $"Error: {ex.Message}";
        }
        finally
        {
            IsLoading = false;
        }
    }

    public void UpdatePlaylistStats()
    {
        TotalPlaylistCount = PlaylistItems.Count;
        SelectedPlaylistCount = PlaylistItems.Count(i => i.IsSelected);
        PlaylistSelectionSummary = $"{SelectedPlaylistCount} of {TotalPlaylistCount} items selected";
        DownloadButtonText = SelectedPlaylistCount > 0 
            ? $"Download {SelectedPlaylistCount} Selected {(SelectedPlaylistCount == 1 ? "Video" : "Videos")}" 
            : "Select at least 1 video";
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

        var cookie = _cookieService.GetActiveProfile()?.FilePath;
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

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

    [ObservableProperty]
    private string _urlInput = string.Empty;

    [ObservableProperty]
    private bool _isLoading;

    [ObservableProperty]
    private string _statusMessage = string.Empty;

    [ObservableProperty]
    private MediaMetadata? _previewMetadata;

    [ObservableProperty]
    private DownloadMode _selectedMode = DownloadMode.VideoAudio;

    [ObservableProperty]
    private string _selectedQuality = "Best";

    [ObservableProperty]
    private string _selectedAudioFormat = "mp3";

    [ObservableProperty]
    private List<string> _availableQualities = new();

    public List<string> AudioFormats { get; } = new() { "mp3", "m4a", "opus", "flac", "wav" };

    public HomeViewModel(
        IMetadataService metadataService,
        IQueueService queueService,
        ISettingsService settingsService,
        ICookieService cookieService)
    {
        _metadataService = metadataService;
        _queueService = queueService;
        _settingsService = settingsService;
        _cookieService = cookieService;
    }

    [RelayCommand]
    public async Task FetchMetadataAsync()
    {
        if (string.IsNullOrWhiteSpace(UrlInput)) return;

        IsLoading = true;
        StatusMessage = "Fetching video details...";
        PreviewMetadata = null;

        try
        {
            var cookie = _cookieService.GetActiveProfile()?.FilePath;
            var meta = await _metadataService.FetchMetadataAsync(UrlInput.Trim(), cookie);
            PreviewMetadata = meta;

            if (meta != null)
            {
                var qList = new List<string> { "Best" };
                qList.AddRange(meta.AvailableQualities);
                AvailableQualities = qList;
                SelectedQuality = "Best";
                StatusMessage = string.Empty;
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

    [RelayCommand]
    public void StartDownload()
    {
        if (PreviewMetadata == null) return;

        var cookie = _cookieService.GetActiveProfile()?.FilePath;
        string formatSpec = _metadataService.GetFormatSpec(PreviewMetadata, SelectedQuality, SelectedMode);

        if (PreviewMetadata.IsPlaylist)
        {
            var selected = PreviewMetadata.PlaylistEntries.Where(e => e.IsSelectedInPlaylist).ToList();
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
                    Quality = SelectedQuality,
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
                Quality = SelectedQuality,
                AudioFormat = SelectedAudioFormat,
                FormatId = formatSpec,
                CookieFilePath = cookie ?? string.Empty
            };
            _queueService.Enqueue(item);
        }

        UrlInput = string.Empty;
        PreviewMetadata = null;
        StatusMessage = "Download queued successfully!";
    }

    [RelayCommand]
    public void ToggleSelectAllPlaylist(bool selectAll)
    {
        if (PreviewMetadata?.PlaylistEntries == null) return;
        foreach (var entry in PreviewMetadata.PlaylistEntries)
        {
            entry.IsSelectedInPlaylist = selectAll;
        }
    }
}

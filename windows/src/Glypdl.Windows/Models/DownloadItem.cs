using CommunityToolkit.Mvvm.ComponentModel;

namespace Glypdl.Windows.Models;

public partial class DownloadItem : ObservableObject
{
    public string Id { get; set; } = Guid.NewGuid().ToString();

    [ObservableProperty]
    private string _url = string.Empty;

    [ObservableProperty]
    private string _title = string.Empty;

    [ObservableProperty]
    private string _uploader = string.Empty;

    [ObservableProperty]
    private int _duration;

    [ObservableProperty]
    private string _thumbnailUrl = string.Empty;

    [ObservableProperty]
    private string _thumbnailLocalPath = string.Empty;

    [ObservableProperty]
    private DownloadState _state = DownloadState.Queued;

    [ObservableProperty]
    private DownloadMode _mode = DownloadMode.VideoAudio;

    [ObservableProperty]
    private string _quality = "best";

    [ObservableProperty]
    private string _audioFormat = "mp3";

    [ObservableProperty]
    private string _formatId = string.Empty;

    [ObservableProperty]
    private string _outputPath = string.Empty;

    [ObservableProperty]
    private string _downloadDirectory = string.Empty;

    [ObservableProperty]
    private string _cookieFilePath = string.Empty;

    [ObservableProperty]
    private int? _playlistIndex;

    [ObservableProperty]
    private double _progress;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(FormattedSize))]
    private long _downloadedBytes;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(FormattedSize))]
    private long _totalBytes;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(FormattedSpeed))]
    private double _speed;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(FormattedEta))]
    private int _etaSeconds;

    [ObservableProperty]
    private string _statusMessage = "Queued";

    [ObservableProperty]
    private string _errorMessage = string.Empty;

    [ObservableProperty]
    private DateTime _createdAt = DateTime.UtcNow;

    [ObservableProperty]
    private DateTime? _completedAt;

    public System.Diagnostics.Process? RunningProcess { get; set; }
    public CancellationTokenSource? CancellationTokenSource { get; set; }

    public string FormattedSpeed => Utilities.FormattingUtils.FormatSpeed(Speed);
    public string FormattedEta => Utilities.FormattingUtils.FormatEta(EtaSeconds);
    public string FormattedSize => $"{Utilities.FormattingUtils.FormatSize(DownloadedBytes)} / {Utilities.FormattingUtils.FormatSize(TotalBytes)}";
}

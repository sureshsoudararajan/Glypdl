namespace Glypdl.Windows.Models;

public enum DownloadState
{
    FetchingInfo,
    Queued,
    Downloading,
    Paused,
    Processing,
    Merging,
    Converting,
    Completed,
    Failed,
    Cancelled
}

public enum DownloadMode
{
    VideoAudio,
    VideoOnly,
    AudioOnly
}

public enum AppTheme
{
    System,
    Light,
    Dark
}

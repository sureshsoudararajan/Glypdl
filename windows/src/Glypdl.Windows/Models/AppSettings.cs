namespace Glypdl.Windows.Models;

public class AppSettings
{
    public string DownloadDirectory { get; set; } = string.Empty;
    public int MaxConcurrentDownloads { get; set; } = 2;
    public bool AutoStartDownloads { get; set; } = false;
    public bool EnableNotifications { get; set; } = true;
    public AppTheme Theme { get; set; } = AppTheme.System;
    public string FilenameTemplate { get; set; } = "%(title)s.%(ext)s";
    public bool OverwriteExisting { get; set; } = false;
    public string CustomYtDlpPath { get; set; } = string.Empty;
    public string CustomFFmpegPath { get; set; } = string.Empty;
    public string ExtraArguments { get; set; } = string.Empty;
    public bool EnableDebugLogging { get; set; } = false;
    public bool UseCookies { get; set; } = false;
    public string ActiveCookieProfileId { get; set; } = string.Empty;
}

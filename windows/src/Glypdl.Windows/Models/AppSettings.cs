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
    public string CookieMethod { get; set; } = "none"; // "none", "browser", "file"
    public string ActiveCookieProfileId { get; set; } = string.Empty;
    public string ActiveCookieFile { get; set; } = string.Empty;
    public string BrowserName { get; set; } = "edge";
    public string BrowserProfile { get; set; } = string.Empty;
    public string BrowserKeyring { get; set; } = "auto";

    public string GetEffectiveCookieMethod()
    {
        if (CookieMethod == "browser" || CookieMethod == "file") return CookieMethod;
        if (UseCookies) return !string.IsNullOrWhiteSpace(ActiveCookieFile) || !string.IsNullOrWhiteSpace(ActiveCookieProfileId) ? "file" : "none";
        return "none";
    }
}

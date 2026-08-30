namespace Glypdl.Windows.Services;

public class UpdateCheckResult
{
    public bool HasUpdate { get; set; }
    public string LatestVersion { get; set; } = string.Empty;
    public string CurrentVersion { get; set; } = "1.0.0";
    public string ReleaseNotes { get; set; } = string.Empty;
    public string DownloadUrl { get; set; } = string.Empty;
}

public interface IUpdateService
{
    Task<UpdateCheckResult> CheckForUpdatesAsync();
}

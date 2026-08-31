using Glypdl.Windows.Models;

namespace Glypdl.Windows.Services;

public interface IYtDlpService
{
    string? DetectYtDlp();
    string? DetectFFmpeg();
    bool IsYtDlpAvailable();
    bool IsFFmpegAvailable();
    Task<string?> GetVersionAsync();
    Task<bool> EnsureBinariesAsync(IProgress<string>? progress = null);
    Task<string> UpdateYtDlpAsync();
    List<string> BuildMetadataArguments(string url, string? cookieFile = null);
    List<string> BuildPlaylistArguments(string url, string? cookieFile = null);
    List<string> BuildDownloadArguments(
        string url,
        string? formatSpec = null,
        string? outputTemplate = null,
        string? downloadDir = null,
        string? cookieFile = null,
        bool extractAudio = false,
        string? audioFormat = null,
        string? audioQuality = null,
        string? extraArgs = null);
}

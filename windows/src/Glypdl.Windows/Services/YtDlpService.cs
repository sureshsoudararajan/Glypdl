using System.Diagnostics;
using System.IO.Compression;
using Glypdl.Windows.Utilities;

namespace Glypdl.Windows.Services;

public class YtDlpService : IYtDlpService
{
    private readonly ISettingsService _settingsService;

    public YtDlpService(ISettingsService settingsService)
    {
        _settingsService = settingsService;
    }

    public string? DetectYtDlp()
    {
        // 1. Check custom path in settings
        var settings = _settingsService.GetSettings();
        if (!string.IsNullOrWhiteSpace(settings.CustomYtDlpPath) && File.Exists(settings.CustomYtDlpPath))
        {
            return settings.CustomYtDlpPath;
        }

        // 2. Check application bin directory in LocalAppData
        string localBin = Path.Combine(PathUtils.GetBinDir(), "yt-dlp.exe");
        if (File.Exists(localBin))
        {
            return localBin;
        }

        // 3. Check application execution directory
        string baseDir = AppDomain.CurrentDomain.BaseDirectory;
        string appDirBin = Path.Combine(baseDir, "yt-dlp.exe");
        if (File.Exists(appDirBin))
        {
            return appDirBin;
        }

        // 4. Check Windows PATH
        return FindOnPath("yt-dlp.exe") ?? FindOnPath("yt-dlp");
    }

    public string? DetectFFmpeg()
    {
        var settings = _settingsService.GetSettings();
        if (!string.IsNullOrWhiteSpace(settings.CustomFFmpegPath) && File.Exists(settings.CustomFFmpegPath))
        {
            return settings.CustomFFmpegPath;
        }

        string localBin = Path.Combine(PathUtils.GetBinDir(), "ffmpeg.exe");
        if (File.Exists(localBin))
        {
            return localBin;
        }

        string baseDir = AppDomain.CurrentDomain.BaseDirectory;
        string appDirBin = Path.Combine(baseDir, "ffmpeg.exe");
        if (File.Exists(appDirBin))
        {
            return appDirBin;
        }

        return FindOnPath("ffmpeg.exe") ?? FindOnPath("ffmpeg");
    }

    public bool IsYtDlpAvailable() => DetectYtDlp() != null;

    public bool IsFFmpegAvailable() => DetectFFmpeg() != null;

    public async Task<string?> GetVersionAsync()
    {
        string? binary = DetectYtDlp();
        if (binary == null)
        {
            await EnsureBinariesAsync();
            binary = DetectYtDlp();
        }
        if (binary == null) return null;

        try
        {
            var res = await ProcessRunner.RunAsync(binary, new[] { "--version" });
            return res.Success ? res.StandardOutput.Trim() : null;
        }
        catch
        {
            return null;
        }
    }

    public async Task<bool> EnsureBinariesAsync(IProgress<string>? progress = null)
    {
        string binDir = PathUtils.GetBinDir();
        string ytDlpPath = Path.Combine(binDir, "yt-dlp.exe");

        // 1. Ensure yt-dlp.exe
        if (!File.Exists(ytDlpPath) && DetectYtDlp() == null)
        {
            try
            {
                progress?.Report("Downloading yt-dlp engine...");
                using var http = new HttpClient();
                http.Timeout = TimeSpan.FromMinutes(2);
                var ytDlpBytes = await http.GetByteArrayAsync("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe");
                await File.WriteAllBytesAsync(ytDlpPath, ytDlpBytes);
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"Failed to auto-download yt-dlp: {ex.Message}");
            }
        }

        // 2. Ensure ffmpeg.exe & ffprobe.exe
        string ffmpegPath = Path.Combine(binDir, "ffmpeg.exe");
        if (!File.Exists(ffmpegPath) && DetectFFmpeg() == null)
        {
            try
            {
                progress?.Report("Downloading FFmpeg converter...");
                using var http = new HttpClient();
                http.Timeout = TimeSpan.FromMinutes(5);
                var zipBytes = await http.GetByteArrayAsync("https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip");
                var tempZip = Path.Combine(PathUtils.GetAppDataDir(), "ffmpeg_temp.zip");
                await File.WriteAllBytesAsync(tempZip, zipBytes);
                
                using (var archive = System.IO.Compression.ZipFile.OpenRead(tempZip))
                {
                    foreach (var entry in archive.Entries)
                    {
                        if (entry.Name.Equals("ffmpeg.exe", StringComparison.OrdinalIgnoreCase))
                        {
                            entry.ExtractToFile(Path.Combine(binDir, "ffmpeg.exe"), overwrite: true);
                        }
                        else if (entry.Name.Equals("ffprobe.exe", StringComparison.OrdinalIgnoreCase))
                        {
                            entry.ExtractToFile(Path.Combine(binDir, "ffprobe.exe"), overwrite: true);
                        }
                    }
                }
                
                try { File.Delete(tempZip); } catch { }
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"Failed to auto-download ffmpeg: {ex.Message}");
            }
        }

        return IsYtDlpAvailable();
    }

    public async Task<string> UpdateYtDlpAsync()
    {
        string? binary = DetectYtDlp();
        if (binary == null)
        {
            await EnsureBinariesAsync();
            binary = DetectYtDlp();
        }
        if (binary == null) return "yt-dlp binary not found.";

        try
        {
            var res = await ProcessRunner.RunAsync(binary, new[] { "-U" });
            return res.Success ? (res.StandardOutput.Trim() + "\n" + res.StandardError.Trim()).Trim() : "Update failed: " + res.StandardError;
        }
        catch (Exception ex)
        {
            return "Update failed: " + ex.Message;
        }
    }

    public List<string> BuildMetadataArguments(string url, string? cookieFile = null)
    {
        var args = new List<string> { "-J", "--no-warnings", "--no-playlist" };
        if (!string.IsNullOrWhiteSpace(cookieFile) && File.Exists(cookieFile))
        {
            args.Add("--cookies");
            args.Add(cookieFile);
        }
        args.Add(url);
        return args;
    }

    public List<string> BuildPlaylistArguments(string url, string? cookieFile = null)
    {
        var args = new List<string> { "-J", "--flat-playlist", "--no-warnings" };
        if (!string.IsNullOrWhiteSpace(cookieFile) && File.Exists(cookieFile))
        {
            args.Add("--cookies");
            args.Add(cookieFile);
        }
        args.Add(url);
        return args;
    }

    public List<string> BuildDownloadArguments(
        string url,
        string? formatSpec = null,
        string? outputTemplate = null,
        string? downloadDir = null,
        string? cookieFile = null,
        bool extractAudio = false,
        string? audioFormat = null,
        string? audioQuality = null,
        string? extraArgs = null)
    {
        var args = new List<string> { "--newline", "--progress", "--no-warnings", "--no-overwrites" };

        string? ffmpeg = DetectFFmpeg();
        if (ffmpeg != null)
        {
            string? dir = Path.GetDirectoryName(ffmpeg);
            if (!string.IsNullOrWhiteSpace(dir))
            {
                args.Add("--ffmpeg-location");
                args.Add(dir);
            }
        }

        if (extractAudio)
        {
            args.Add("-x");
            string fmt = (!string.IsNullOrWhiteSpace(audioFormat) && !audioFormat.Equals("best", StringComparison.OrdinalIgnoreCase))
                ? audioFormat.ToLowerInvariant()
                : "mp3";
            args.Add("--audio-format");
            args.Add(fmt);

            string qualityVal = "0"; // best (~320k)
            if (!string.IsNullOrWhiteSpace(audioQuality))
            {
                if (audioQuality.Contains("320")) qualityVal = "0";
                else if (audioQuality.Contains("256")) qualityVal = "2";
                else if (audioQuality.Contains("192")) qualityVal = "4";
                else if (audioQuality.Contains("128")) qualityVal = "5";
                else if (audioQuality.Contains("96")) qualityVal = "7";
            }
            args.Add("--audio-quality");
            args.Add(qualityVal);
        }

        if (!string.IsNullOrWhiteSpace(formatSpec))
        {
            args.Add("-f");
            args.Add(formatSpec);
        }

        if (!string.IsNullOrWhiteSpace(outputTemplate))
        {
            args.Add("-o");
            args.Add(outputTemplate);
        }

        if (!string.IsNullOrWhiteSpace(downloadDir))
        {
            args.Add("-P");
            args.Add(downloadDir);
        }

        if (!string.IsNullOrWhiteSpace(cookieFile) && File.Exists(cookieFile))
        {
            args.Add("--cookies");
            args.Add(cookieFile);
        }

        if (!string.IsNullOrWhiteSpace(extraArgs))
        {
            foreach (var piece in extraArgs.Split(' ', StringSplitOptions.RemoveEmptyEntries))
            {
                args.Add(piece);
            }
        }

        args.Add(url);
        return args;
    }

    private static string? FindOnPath(string exeName)
    {
        string? pathEnv = Environment.GetEnvironmentVariable("PATH");
        if (string.IsNullOrWhiteSpace(pathEnv)) return null;

        foreach (var p in pathEnv.Split(Path.PathSeparator, StringSplitOptions.RemoveEmptyEntries))
        {
            try
            {
                string full = Path.Combine(p.Trim(), exeName);
                if (File.Exists(full)) return full;
            }
            catch { }
        }
        return null;
    }
}

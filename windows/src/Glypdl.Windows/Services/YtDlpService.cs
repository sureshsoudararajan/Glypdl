using System.Diagnostics;
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
        string? extraArgs = null)
    {
        var args = new List<string> { "--newline", "--progress", "--no-warnings" };

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
            if (!string.IsNullOrWhiteSpace(audioFormat) && !audioFormat.Equals("best", StringComparison.OrdinalIgnoreCase))
            {
                args.Add("--audio-format");
                args.Add(audioFormat.ToLowerInvariant());
            }
            args.Add("--audio-quality");
            args.Add("0");
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

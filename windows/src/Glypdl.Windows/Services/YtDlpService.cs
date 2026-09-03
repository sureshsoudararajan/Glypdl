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

        // 2. Check application execution directory (bundled with the app)
        string baseDir = AppDomain.CurrentDomain.BaseDirectory;
        string appDirBin = Path.Combine(baseDir, "yt-dlp.exe");
        if (File.Exists(appDirBin))
        {
            return appDirBin;
        }
        string appDirSubBin = Path.Combine(baseDir, "bin_bundle", "yt-dlp.exe");
        if (File.Exists(appDirSubBin))
        {
            return appDirSubBin;
        }

        // 3. Check application bin directory in LocalAppData
        string localBin = Path.Combine(PathUtils.GetBinDir(), "yt-dlp.exe");
        if (File.Exists(localBin))
        {
            return localBin;
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

        // 1. Check application execution directory (bundled with the app)
        string baseDir = AppDomain.CurrentDomain.BaseDirectory;
        string appDirBin = Path.Combine(baseDir, "ffmpeg.exe");
        if (File.Exists(appDirBin))
        {
            return appDirBin;
        }
        string appDirSubBin = Path.Combine(baseDir, "bin_bundle", "ffmpeg.exe");
        if (File.Exists(appDirSubBin))
        {
            return appDirSubBin;
        }

        // 2. Check application bin directory in LocalAppData
        string localBin = Path.Combine(PathUtils.GetBinDir(), "ffmpeg.exe");
        if (File.Exists(localBin))
        {
            return localBin;
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

    public async Task<string?> GetFFmpegVersionAsync()
    {
        string? binary = DetectFFmpeg();
        if (binary == null) return null;

        try
        {
            var res = await ProcessRunner.RunAsync(binary, new[] { "-version" });
            if (res.Success && !string.IsNullOrWhiteSpace(res.StandardOutput))
            {
                var firstLine = res.StandardOutput.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries).FirstOrDefault();
                if (firstLine != null && firstLine.StartsWith("ffmpeg version", StringComparison.OrdinalIgnoreCase))
                {
                    var parts = firstLine.Split(' ', StringSplitOptions.RemoveEmptyEntries);
                    if (parts.Length >= 3)
                    {
                        return parts[2];
                    }
                }
                return firstLine?.Trim();
            }
            return null;
        }
        catch
        {
            return null;
        }
    }

    public bool NeedsBinariesSetup()
    {
        return !IsYtDlpAvailable() || !IsFFmpegAvailable();
    }

    public async Task<bool> EnsureBinariesAsync(IProgress<string>? progress = null)
    {
        var wrapper = progress != null 
            ? new Progress<EngineSetupProgress>(p => progress.Report($"{p.Stage}: {p.Details}"))
            : null;
        return await EnsureBinariesWithProgressAsync(wrapper);
    }

    public async Task<bool> EnsureBinariesWithProgressAsync(IProgress<EngineSetupProgress>? progress = null)
    {
        string binDir = PathUtils.GetBinDir();
        Directory.CreateDirectory(binDir);

        bool needYtDlp = !File.Exists(Path.Combine(binDir, "yt-dlp.exe")) && DetectYtDlp() == null;
        bool needFFmpeg = !File.Exists(Path.Combine(binDir, "ffmpeg.exe")) && DetectFFmpeg() == null;

        if (!needYtDlp && !needFFmpeg)
        {
            return true;
        }

        using var http = new HttpClient();
        http.Timeout = TimeSpan.FromMinutes(10);
        http.DefaultRequestHeaders.UserAgent.ParseAdd("Glypdl/1.0.0 (Windows; Native Client)");

        // 1. Ensure yt-dlp.exe
        if (needYtDlp)
        {
            string ytDlpPath = Path.Combine(binDir, "yt-dlp.exe");
            string ytDlpTemp = Path.Combine(binDir, "yt-dlp.exe.tmp");
            try
            {
                progress?.Report(new EngineSetupProgress("Downloading yt-dlp engine (1/2)...", "Connecting to GitHub...", 0, true));
                double maxPercent = needFFmpeg ? 25.0 : 100.0;
                await DownloadFileWithProgressAsync(http, "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe", ytDlpTemp, progress, "Downloading yt-dlp engine (1/2)...", 0, maxPercent);
                if (File.Exists(ytDlpPath)) File.Delete(ytDlpPath);
                File.Move(ytDlpTemp, ytDlpPath);
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"Failed to auto-download yt-dlp: {ex.Message}");
                try { if (File.Exists(ytDlpTemp)) File.Delete(ytDlpTemp); } catch { }
            }
        }

        // 2. Ensure ffmpeg.exe & ffprobe.exe
        if (needFFmpeg)
        {
            string tempZip = Path.Combine(PathUtils.GetAppDataDir(), "ffmpeg_temp.zip");
            try
            {
                double startPercent = needYtDlp ? 25.0 : 0.0;
                double endPercent = 90.0;
                progress?.Report(new EngineSetupProgress("Downloading FFmpeg & FFprobe (2/2)...", "Connecting to GitHub...", startPercent, true));
                await DownloadFileWithProgressAsync(http, "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip", tempZip, progress, "Downloading FFmpeg & FFprobe (2/2)...", startPercent, endPercent);

                progress?.Report(new EngineSetupProgress("Extracting FFmpeg & FFprobe...", "Unpacking converter binaries...", 92, true));
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
                progress?.Report(new EngineSetupProgress("Setup Complete!", "Media engines are ready.", 100, false));
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"Failed to auto-download ffmpeg: {ex.Message}");
            }
            finally
            {
                try { if (File.Exists(tempZip)) File.Delete(tempZip); } catch { }
            }
        }

        return IsYtDlpAvailable();
    }

    private static async Task DownloadFileWithProgressAsync(
        HttpClient client,
        string url,
        string destinationPath,
        IProgress<EngineSetupProgress>? progress,
        string stageTitle,
        double startPercent,
        double endPercent)
    {
        using var response = await client.GetAsync(url, HttpCompletionOption.ResponseHeadersRead);
        response.EnsureSuccessStatusCode();

        long? totalBytes = response.Content.Headers.ContentLength;
        using var contentStream = await response.Content.ReadAsStreamAsync();
        using var fileStream = new FileStream(destinationPath, FileMode.Create, FileAccess.Write, FileShare.None, 81920, true);

        var buffer = new byte[81920];
        long totalRead = 0;
        int bytesRead;

        while ((bytesRead = await contentStream.ReadAsync(buffer, 0, buffer.Length)) > 0)
        {
            await fileStream.WriteAsync(buffer, 0, bytesRead);
            totalRead += bytesRead;

            if (totalBytes.HasValue && totalBytes.Value > 0)
            {
                double fileFraction = (double)totalRead / totalBytes.Value;
                double overallPercent = startPercent + (fileFraction * (endPercent - startPercent));
                string details = $"{totalRead / 1048576.0:F1} MB / {totalBytes.Value / 1048576.0:F1} MB ({Math.Round(fileFraction * 100)}%)";
                progress?.Report(new EngineSetupProgress(stageTitle, details, overallPercent, false));
            }
            else
            {
                string details = $"{totalRead / 1048576.0:F1} MB downloaded";
                progress?.Report(new EngineSetupProgress(stageTitle, details, startPercent, true));
            }
        }
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

    public static string? ExtractInstagramStoryId(string url)
    {
        if (string.IsNullOrWhiteSpace(url)) return null;

        var match = System.Text.RegularExpressions.Regex.Match(
            url,
            @"instagram\.com/stories/(?:highlights/[^/?#]+/|[^/?#]+/)(?<id>\d+)",
            System.Text.RegularExpressions.RegexOptions.IgnoreCase
        );

        if (match.Success && match.Groups["id"].Success)
        {
            return match.Groups["id"].Value;
        }

        return null;
    }

    public List<string> BuildMetadataArguments(string url, string? cookieFile = null)
    {
        var args = new List<string> { "-J", "--no-warnings", "--no-playlist" };
        AppendCookieArgument(args, cookieFile);
        args.Add(url);
        return args;
    }

    public List<string> BuildPlaylistArguments(string url, string? cookieFile = null)
    {
        var args = new List<string> { "-J", "--flat-playlist", "--no-warnings" };
        AppendCookieArgument(args, cookieFile);
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

        string? storyId = ExtractInstagramStoryId(url);
        if (!string.IsNullOrWhiteSpace(storyId))
        {
            args.Add("--match-filter");
            args.Add($"id ~= {storyId} | webpage_url ~= {storyId} | original_url ~= {storyId}");
        }

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
                : "webm";
            if (fmt == "mp4") fmt = "m4a";
            if (fmt == "webm") fmt = "best";
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
        else
        {
            args.Add("--merge-output-format");
            args.Add("mp4");
        }

        if (!string.IsNullOrWhiteSpace(formatSpec))
        {
            args.Add("-f");
            args.Add(formatSpec);
        }
        else if (extractAudio)
        {
            args.Add("-f");
            args.Add("ba/b");
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

        AppendCookieArgument(args, cookieFile);

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

    private static void AppendCookieArgument(List<string> args, string? cookieSource)
    {
        if (string.IsNullOrWhiteSpace(cookieSource)) return;

        string trimmed = cookieSource.Trim();
        if (trimmed.StartsWith("browser:", StringComparison.OrdinalIgnoreCase))
        {
            string spec = trimmed.Substring(8).Trim();
            if (!string.IsNullOrWhiteSpace(spec))
            {
                args.Add("--cookies-from-browser");
                args.Add(spec);
            }
        }
        else if (File.Exists(trimmed))
        {
            args.Add("--cookies");
            args.Add(trimmed);
        }
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

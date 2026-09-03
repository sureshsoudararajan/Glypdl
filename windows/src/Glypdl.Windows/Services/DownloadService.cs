using System.Diagnostics;
using Glypdl.Windows.Models;
using Glypdl.Windows.Utilities;

namespace Glypdl.Windows.Services;

public class DownloadService : IDownloadService
{
    private readonly IYtDlpService _ytdlpService;
    private readonly ISettingsService _settingsService;
    private readonly IHistoryService _historyService;
    private readonly INotificationService _notificationService;

    public DownloadService(
        IYtDlpService ytdlpService,
        ISettingsService settingsService,
        IHistoryService historyService,
        INotificationService notificationService)
    {
        _ytdlpService = ytdlpService;
        _settingsService = settingsService;
        _historyService = historyService;
        _notificationService = notificationService;
    }

    public async Task ExecuteDownloadAsync(DownloadItem item, CancellationToken cancellationToken)
    {
        string? binary = _ytdlpService.DetectYtDlp();
        if (binary == null)
        {
            await _ytdlpService.EnsureBinariesAsync();
            binary = _ytdlpService.DetectYtDlp();
            if (binary == null)
            {
                item.State = DownloadState.Failed;
                item.ErrorMessage = "yt-dlp executable not found and could not be downloaded.";
                return;
            }
        }

        var settings = _settingsService.GetSettings();
        string downloadDir = !string.IsNullOrWhiteSpace(item.DownloadDirectory)
            ? item.DownloadDirectory
            : (!string.IsNullOrWhiteSpace(settings.DownloadDirectory) ? settings.DownloadDirectory : PathUtils.GetDefaultDownloadDirectory());

        Directory.CreateDirectory(downloadDir);

        var args = _ytdlpService.BuildDownloadArguments(
            url: item.Url,
            formatSpec: item.FormatId,
            outputTemplate: settings.FilenameTemplate,
            downloadDir: downloadDir,
            cookieFile: item.CookieFilePath,
            extractAudio: item.Mode == DownloadMode.AudioOnly,
            audioFormat: item.AudioFormat,
            audioQuality: item.Quality,
            extraArgs: settings.ExtraArguments,
            playlistIndex: item.PlaylistIndex
        );

        DispatcherHelper.ExecuteOnUIThread(() =>
        {
            item.State = DownloadState.Downloading;
            item.StatusMessage = "Starting download...";
        });

        var tcs = new TaskCompletionSource<int>();
        string? capturedDestination = null;

        var process = ProcessRunner.StartStreaming(
            executable: binary,
            arguments: args,
            onLine: line =>
            {
                if (!string.IsNullOrWhiteSpace(line))
                {
                    string trimmed = line.Trim();
                    if (trimmed.StartsWith("[download] Destination:", StringComparison.OrdinalIgnoreCase))
                    {
                        capturedDestination = trimmed.Substring("[download] Destination:".Length).Trim().Trim('"').Trim('\'');
                    }
                    else if (trimmed.Contains("Merging formats into", StringComparison.OrdinalIgnoreCase))
                    {
                        int idx = trimmed.IndexOf("into", StringComparison.OrdinalIgnoreCase);
                        if (idx >= 0)
                        {
                            capturedDestination = trimmed.Substring(idx + 4).Trim().Trim('"').Trim('\'');
                        }
                    }
                    else if (trimmed.StartsWith("[ExtractAudio] Destination:", StringComparison.OrdinalIgnoreCase))
                    {
                        capturedDestination = trimmed.Substring("[ExtractAudio] Destination:".Length).Trim().Trim('"').Trim('\'');
                    }
                    else if (trimmed.Contains(" in ", StringComparison.OrdinalIgnoreCase) && (trimmed.StartsWith("[Fixup", StringComparison.OrdinalIgnoreCase) || trimmed.StartsWith("[VideoConvertor", StringComparison.OrdinalIgnoreCase)))
                    {
                        int idx = trimmed.IndexOf(" in ", StringComparison.OrdinalIgnoreCase);
                        if (idx >= 0)
                        {
                            capturedDestination = trimmed.Substring(idx + 4).Trim().Trim('"').Trim('\'');
                        }
                    }
                    else if (trimmed.StartsWith("[MoveFiles] Moving file", StringComparison.OrdinalIgnoreCase) && trimmed.Contains(" to "))
                    {
                        int idx = trimmed.LastIndexOf(" to ", StringComparison.OrdinalIgnoreCase);
                        if (idx >= 0)
                        {
                            capturedDestination = trimmed.Substring(idx + 4).Trim().Trim('"').Trim('\'');
                        }
                    }
                    else if (trimmed.StartsWith("[download] ", StringComparison.OrdinalIgnoreCase) && trimmed.Contains(" has already been downloaded", StringComparison.OrdinalIgnoreCase))
                    {
                        string part = trimmed.Substring("[download] ".Length);
                        int idx = part.IndexOf(" has already been downloaded", StringComparison.OrdinalIgnoreCase);
                        if (idx > 0)
                        {
                            capturedDestination = part.Substring(0, idx).Trim().Trim('"').Trim('\'');
                        }
                    }
                }

                var progress = FormattingUtils.ParseProgressLine(line);
                if (progress != null)
                {
                    DispatcherHelper.ExecuteOnUIThread(() =>
                    {
                        if (progress.Percent.HasValue)
                            item.Progress = progress.Percent.Value;
                        if (progress.DownloadedBytes.HasValue)
                            item.DownloadedBytes = progress.DownloadedBytes.Value;
                        if (progress.TotalBytes.HasValue)
                            item.TotalBytes = progress.TotalBytes.Value;
                        if (progress.SpeedBytesPerSec.HasValue)
                            item.Speed = progress.SpeedBytesPerSec.Value;
                        if (progress.EtaSeconds.HasValue)
                            item.EtaSeconds = progress.EtaSeconds.Value;
                        if (!string.IsNullOrWhiteSpace(progress.Status))
                            item.StatusMessage = progress.Status;
                    });
                }
            },
            onError: errLine =>
            {
                if (!string.IsNullOrWhiteSpace(errLine) && !errLine.Contains("WARNING:"))
                {
                    DispatcherHelper.ExecuteOnUIThread(() =>
                    {
                        item.ErrorMessage = errLine;
                    });
                }
            },
            workingDirectory: downloadDir
        );

        item.RunningProcess = process;

        using (cancellationToken.Register(() =>
        {
            try { process.Kill(); } catch { }
            tcs.TrySetCanceled();
        }))
        {
            await process.WaitForExitAsync(cancellationToken);
            tcs.TrySetResult(process.ExitCode);
        }

        int exitCode = await tcs.Task;

        if (cancellationToken.IsCancellationRequested)
        {
            DispatcherHelper.ExecuteOnUIThread(() =>
            {
                item.State = DownloadState.Cancelled;
                item.StatusMessage = "Cancelled";
            });
            return;
        }

        if (exitCode == 0)
        {
            long actualFileSize = item.TotalBytes > 0 ? item.TotalBytes : item.DownloadedBytes;
            string destinationFile = downloadDir;

            try
            {
                if (!string.IsNullOrWhiteSpace(capturedDestination))
                {
                    if (!Path.IsPathRooted(capturedDestination))
                    {
                        capturedDestination = Path.Combine(downloadDir, capturedDestination);
                    }
                    if (File.Exists(capturedDestination))
                    {
                        destinationFile = capturedDestination;
                        actualFileSize = new FileInfo(capturedDestination).Length;
                    }
                }

                if ((actualFileSize <= 0 || destinationFile == downloadDir) && Directory.Exists(downloadDir))
                {
                    var files = Directory.GetFiles(downloadDir);
                    var cleanTitle = string.Concat(item.Title.Where(c => !Path.GetInvalidFileNameChars().Contains(c))).Trim();
                    if (!string.IsNullOrWhiteSpace(cleanTitle))
                    {
                        var matched = files.Where(f =>
                            Path.GetFileNameWithoutExtension(f).Contains(cleanTitle, StringComparison.OrdinalIgnoreCase) ||
                            cleanTitle.Contains(Path.GetFileNameWithoutExtension(f), StringComparison.OrdinalIgnoreCase))
                            .OrderByDescending(f => File.GetLastWriteTimeUtc(f))
                            .FirstOrDefault();

                        if (matched != null && File.Exists(matched))
                        {
                            actualFileSize = new FileInfo(matched).Length;
                            destinationFile = matched;
                        }
                    }

                    if (actualFileSize <= 0 || destinationFile == downloadDir)
                    {
                        var mediaExts = new[] { ".mp4", ".mkv", ".webm", ".mp3", ".m4a", ".opus", ".flac", ".wav", ".aac" };
                        var candidate = files.Where(f => mediaExts.Contains(Path.GetExtension(f).ToLowerInvariant()))
                                             .OrderByDescending(f => File.GetLastWriteTimeUtc(f))
                                             .FirstOrDefault();
                        if (candidate != null && File.Exists(candidate))
                        {
                            actualFileSize = new FileInfo(candidate).Length;
                            destinationFile = candidate;
                        }
                    }
                }
            }
            catch { }

            DispatcherHelper.ExecuteOnUIThread(() =>
            {
                item.State = DownloadState.Completed;
                item.StatusMessage = "Completed";
                item.Progress = 100;
                item.Speed = 0;
                item.EtaSeconds = 0;
                if (actualFileSize > 0)
                {
                    item.DownloadedBytes = actualFileSize;
                    item.TotalBytes = actualFileSize;
                }
                if (File.Exists(destinationFile))
                {
                    item.OutputPath = destinationFile;
                    item.DownloadDirectory = Path.GetDirectoryName(destinationFile) ?? downloadDir;
                }
                item.CompletedAt = DateTime.UtcNow;
            });

            string thumbPath = item.ThumbnailLocalPath;
            if (string.IsNullOrWhiteSpace(thumbPath) && !string.IsNullOrWhiteSpace(item.ThumbnailUrl))
            {
                try
                {
                    var thumbDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Glypdl", "thumbnails");
                    Directory.CreateDirectory(thumbDir);
                    var cleanId = Guid.NewGuid().ToString("N");
                    var filePath = Path.Combine(thumbDir, $"{cleanId}.jpg");
                    using var http = new HttpClient();
                    var bytes = await http.GetByteArrayAsync(item.ThumbnailUrl, cancellationToken);
                    await File.WriteAllBytesAsync(filePath, bytes, cancellationToken);
                    thumbPath = filePath;
                    item.ThumbnailLocalPath = filePath;
                }
                catch { }
            }
            if (string.IsNullOrWhiteSpace(thumbPath))
            {
                thumbPath = item.ThumbnailUrl;
            }

            await _historyService.AddEntryAsync(new HistoryEntry
            {
                Url = item.Url,
                Title = item.Title,
                Uploader = item.Uploader,
                ThumbnailPath = thumbPath,
                DownloadPath = destinationFile,
                Format = item.FormatId,
                FileSize = actualFileSize,
                Status = "Completed",
                Timestamp = DateTime.UtcNow,
                Duration = item.Duration,
                Mode = item.Mode.ToString(),
                Quality = item.Quality
            });

            _notificationService.ShowDownloadCompleted(item);
        }
        else
        {
            DispatcherHelper.ExecuteOnUIThread(() =>
            {
                item.State = DownloadState.Failed;
                item.StatusMessage = "Failed";
                if (string.IsNullOrWhiteSpace(item.ErrorMessage))
                {
                    item.ErrorMessage = $"yt-dlp exited with code {exitCode}";
                }
            });
            _notificationService.ShowDownloadFailed(item);
        }
    }

    public void CancelDownload(DownloadItem item)
    {
        try
        {
            item.CancellationTokenSource?.Cancel();
            if (item.RunningProcess != null && !item.RunningProcess.HasExited)
            {
                item.RunningProcess.Kill(true);
            }
        }
        catch { }
        finally
        {
            DispatcherHelper.ExecuteOnUIThread(() =>
            {
                item.State = DownloadState.Cancelled;
                item.StatusMessage = "Cancelled";
            });
        }
    }
}

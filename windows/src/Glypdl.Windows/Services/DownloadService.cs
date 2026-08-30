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
            item.State = DownloadState.Failed;
            item.ErrorMessage = "yt-dlp executable not found.";
            return;
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
            extraArgs: settings.ExtraArguments
        );

        item.State = DownloadState.Downloading;
        item.StatusMessage = "Starting download...";

        var tcs = new TaskCompletionSource<int>();

        var process = ProcessRunner.StartStreaming(
            executable: binary,
            arguments: args,
            onLine: line =>
            {
                var progress = FormattingUtils.ParseProgressLine(line);
                if (progress != null)
                {
                    if (progress.Percent.HasValue) item.Progress = progress.Percent.Value;
                    if (progress.DownloadedBytes.HasValue) item.DownloadedBytes = progress.DownloadedBytes.Value;
                    if (progress.TotalBytes.HasValue) item.TotalBytes = progress.TotalBytes.Value;
                    if (progress.SpeedBytesPerSec.HasValue) item.Speed = progress.SpeedBytesPerSec.Value;
                    if (progress.EtaSeconds.HasValue) item.EtaSeconds = progress.EtaSeconds.Value;
                    if (!string.IsNullOrWhiteSpace(progress.Status)) item.StatusMessage = progress.Status;
                }
            },
            onError: errLine =>
            {
                if (!string.IsNullOrWhiteSpace(errLine) && !errLine.Contains("WARNING:"))
                {
                    item.ErrorMessage = errLine;
                }
            },
            workingDirectory: downloadDir
        );

        item.RunningProcess = process;

        using (cancellationToken.Register(() =>
        {
            try
            {
                if (!process.HasExited)
                {
                    process.Kill(true);
                }
            }
            catch { }
            tcs.TrySetCanceled();
        }))
        {
            await process.WaitForExitAsync(cancellationToken);
            tcs.TrySetResult(process.ExitCode);
        }

        int exitCode = await tcs.Task;

        if (cancellationToken.IsCancellationRequested)
        {
            item.State = DownloadState.Cancelled;
            item.StatusMessage = "Cancelled";
            return;
        }

        if (exitCode == 0)
        {
            item.State = DownloadState.Completed;
            item.StatusMessage = "Completed";
            item.Progress = 100;
            item.CompletedAt = DateTime.UtcNow;

            await _historyService.AddEntryAsync(new HistoryEntry
            {
                Url = item.Url,
                Title = item.Title,
                Uploader = item.Uploader,
                ThumbnailPath = item.ThumbnailLocalPath,
                DownloadPath = downloadDir,
                Format = item.FormatId,
                FileSize = item.TotalBytes > 0 ? item.TotalBytes : item.DownloadedBytes,
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
            item.State = DownloadState.Failed;
            item.StatusMessage = "Failed";
            if (string.IsNullOrWhiteSpace(item.ErrorMessage))
            {
                item.ErrorMessage = $"yt-dlp exited with code {exitCode}";
            }
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
            item.State = DownloadState.Cancelled;
            item.StatusMessage = "Cancelled";
        }
    }
}

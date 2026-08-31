using System.Collections.ObjectModel;
using Glypdl.Windows.Models;
using Glypdl.Windows.Utilities;

namespace Glypdl.Windows.Services;

public class QueueService : IQueueService
{
    private readonly IDownloadService _downloadService;
    private readonly ISettingsService _settingsService;
    private readonly SemaphoreSlim _semaphore;

    public ObservableCollection<DownloadItem> ActiveDownloads { get; } = new();
    public ObservableCollection<DownloadItem> QueuedDownloads { get; } = new();

    public QueueService(IDownloadService downloadService, ISettingsService settingsService)
    {
        _downloadService = downloadService;
        _settingsService = settingsService;
        int max = _settingsService.GetSettings().MaxConcurrentDownloads;
        _semaphore = new SemaphoreSlim(Math.Max(1, max));
    }

    public void Enqueue(DownloadItem item)
    {
        DispatcherHelper.ExecuteOnUIThread(() =>
        {
            item.State = DownloadState.Queued;
            item.StatusMessage = "Queued";
            QueuedDownloads.Add(item);
        });
        _ = ProcessQueueAsync(item);
    }

    public void Cancel(DownloadItem item)
    {
        DispatcherHelper.ExecuteOnUIThread(() =>
        {
            if (ActiveDownloads.Contains(item))
            {
                _downloadService.CancelDownload(item);
                ActiveDownloads.Remove(item);
            }
            if (QueuedDownloads.Contains(item))
            {
                QueuedDownloads.Remove(item);
            }
        });
    }

    public void Retry(DownloadItem item)
    {
        DispatcherHelper.ExecuteOnUIThread(() =>
        {
            item.Progress = 0;
            item.DownloadedBytes = 0;
            item.TotalBytes = 0;
            item.Speed = 0;
            item.ErrorMessage = string.Empty;
        });
        Enqueue(item);
    }

    public void ClearCompleted()
    {
        DispatcherHelper.ExecuteOnUIThread(() =>
        {
            var completed = ActiveDownloads.Where(d => d.State == DownloadState.Completed || d.State == DownloadState.Cancelled).ToList();
            foreach (var item in completed)
            {
                ActiveDownloads.Remove(item);
            }
        });
    }

    private async Task ProcessQueueAsync(DownloadItem item)
    {
        await _semaphore.WaitAsync();
        try
        {
            DispatcherHelper.ExecuteOnUIThread(() =>
            {
                QueuedDownloads.Remove(item);
                ActiveDownloads.Add(item);
            });

            item.CancellationTokenSource = new CancellationTokenSource();
            await _downloadService.ExecuteDownloadAsync(item, item.CancellationTokenSource.Token);
        }
        catch (Exception ex)
        {
            DispatcherHelper.ExecuteOnUIThread(() =>
            {
                item.State = DownloadState.Failed;
                item.ErrorMessage = ex.Message;
            });
        }
        finally
        {
            _semaphore.Release();
        }
    }
}

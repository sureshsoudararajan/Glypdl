using System.Collections.ObjectModel;
using Glypdl.Windows.Models;

namespace Glypdl.Windows.Services;

public interface IQueueService
{
    ObservableCollection<DownloadItem> ActiveDownloads { get; }
    ObservableCollection<DownloadItem> QueuedDownloads { get; }
    void Enqueue(DownloadItem item);
    void Cancel(DownloadItem item);
    void Retry(DownloadItem item);
    void ClearCompleted();
}

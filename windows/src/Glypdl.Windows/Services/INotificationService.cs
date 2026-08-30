using Glypdl.Windows.Models;

namespace Glypdl.Windows.Services;

public interface INotificationService
{
    void ShowDownloadCompleted(DownloadItem item);
    void ShowDownloadFailed(DownloadItem item);
}

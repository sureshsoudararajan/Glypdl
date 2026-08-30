using Glypdl.Windows.Models;

namespace Glypdl.Windows.Services;

public interface IDownloadService
{
    Task ExecuteDownloadAsync(DownloadItem item, CancellationToken cancellationToken);
    void CancelDownload(DownloadItem item);
}

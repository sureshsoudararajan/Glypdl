using Glypdl.Windows.Models;

namespace Glypdl.Windows.Services;

public class NotificationService : INotificationService
{
    private readonly ISettingsService _settingsService;

    public NotificationService(ISettingsService settingsService)
    {
        _settingsService = settingsService;
    }

    public void ShowDownloadCompleted(DownloadItem item)
    {
        if (!_settingsService.GetSettings().EnableNotifications) return;

        try
        {
            // Windows App SDK Toast Notification / AppNotification
            // Gracefully handled for packaged & unpackaged modes
            System.Diagnostics.Debug.WriteLine($"[Notification] Download completed: {item.Title}");
        }
        catch { }
    }

    public void ShowDownloadFailed(DownloadItem item)
    {
        if (!_settingsService.GetSettings().EnableNotifications) return;

        try
        {
            System.Diagnostics.Debug.WriteLine($"[Notification] Download failed: {item.Title} - {item.ErrorMessage}");
        }
        catch { }
    }
}

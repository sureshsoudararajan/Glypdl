using System.Security;
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

        string title = SecurityElement.Escape(item.Title ?? "Media Download");
        string size = SecurityElement.Escape(item.FormattedSize);
        string mode = SecurityElement.Escape(item.Mode.ToString());

        try
        {
            var xmlDoc = new global::Windows.Data.Xml.Dom.XmlDocument();
            string xml = $@"<toast>
                <visual>
                    <binding template=""ToastGeneric"">
                        <text>Download Completed 🎉</text>
                        <text>{title}</text>
                        <text>{mode} • {size}</text>
                    </binding>
                </visual>
                <audio src=""ms-winsoundevent:Notification.Default"" />
            </toast>";
            xmlDoc.LoadXml(xml);
            var toast = new global::Windows.UI.Notifications.ToastNotification(xmlDoc);
            global::Windows.UI.Notifications.ToastNotificationManager.CreateToastNotifier("Glypdl").Show(toast);
        }
        catch { }
    }

    public void ShowDownloadFailed(DownloadItem item)
    {
        if (!_settingsService.GetSettings().EnableNotifications) return;

        string title = SecurityElement.Escape(item.Title ?? "Media Download");
        string error = SecurityElement.Escape(!string.IsNullOrWhiteSpace(item.ErrorMessage) ? item.ErrorMessage : "Download failed.");

        try
        {
            var xmlDoc = new global::Windows.Data.Xml.Dom.XmlDocument();
            string xml = $@"<toast>
                <visual>
                    <binding template=""ToastGeneric"">
                        <text>Download Failed ❌</text>
                        <text>{title}</text>
                        <text>{error}</text>
                    </binding>
                </visual>
            </toast>";
            xmlDoc.LoadXml(xml);
            var toast = new global::Windows.UI.Notifications.ToastNotification(xmlDoc);
            global::Windows.UI.Notifications.ToastNotificationManager.CreateToastNotifier("Glypdl").Show(toast);
        }
        catch { }
    }
}

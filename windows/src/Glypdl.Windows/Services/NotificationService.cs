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

        string title = item.Title ?? "Media Download";
        string size = item.FormattedSize;
        string mode = item.Mode.ToString();

        try
        {
            SendNotification("Download Completed 🎉", $"{title}\n{mode} • {size}");
        }
        catch { }
    }

    public void ShowDownloadFailed(DownloadItem item)
    {
        if (!_settingsService.GetSettings().EnableNotifications) return;

        string title = item.Title ?? "Media Download";
        string error = !string.IsNullOrWhiteSpace(item.ErrorMessage) ? item.ErrorMessage : "Download failed.";

        try
        {
            SendNotification("Download Failed ❌", $"{title}\n{error}");
        }
        catch { }
    }

    private static void SendNotification(string header, string body)
    {
#if DISABLE_XAML_GENERATED_MAIN
        try
        {
            var notification = new Microsoft.Windows.AppNotifications.Builder.AppNotificationBuilder()
                .AddText(header)
                .AddText(body)
                .BuildNotification();

            Microsoft.Windows.AppNotifications.AppNotificationManager.Default.Show(notification);
            return;
        }
        catch { }
#endif
        try
        {
            var xmlDoc = new global::Windows.Data.Xml.Dom.XmlDocument();
            string escapedHeader = System.Security.SecurityElement.Escape(header);
            string escapedBody = System.Security.SecurityElement.Escape(body);
            string xml = $@"<toast>
                <visual>
                    <binding template=""ToastGeneric"">
                        <text>{escapedHeader}</text>
                        <text>{escapedBody}</text>
                    </binding>
                </visual>
            </toast>";
            xmlDoc.LoadXml(xml);
            var toast = new global::Windows.UI.Notifications.ToastNotification(xmlDoc);
            global::Windows.UI.Notifications.ToastNotificationManager.CreateToastNotifier().Show(toast);
        }
        catch { }
    }
}

using Microsoft.UI.Xaml;
using Glypdl.Windows.Services;
using Glypdl.Windows.ViewModels;

namespace Glypdl.Windows;

public partial class App : Application
{
    private Window? _mainWindow;

    public static IServiceProvider Services { get; private set; } = null!;

    public App()
    {
        InitializeComponent();
        ConfigureServices();
    }

    private void ConfigureServices()
    {
        // Simple DI container setup
        var settingsService = new SettingsService();
        var ytdlpService = new YtDlpService(settingsService);
        var cookieService = new CookieService(settingsService);
        var historyService = new HistoryService();
        var notificationService = new NotificationService(settingsService);
        var updateService = new UpdateService();
        var metadataService = new MetadataService(ytdlpService);
        var downloadService = new DownloadService(ytdlpService, settingsService, historyService, notificationService);
        var queueService = new QueueService(downloadService, settingsService);

        var homeVm = new HomeViewModel(metadataService, queueService, settingsService, cookieService);
        var downloadsVm = new DownloadsViewModel(queueService);
        var historyVm = new HistoryViewModel(historyService, queueService);
        var settingsVm = new SettingsViewModel(settingsService, cookieService, ytdlpService);
        var aboutVm = new AboutViewModel(updateService);

        var mainVm = new MainViewModel(homeVm, downloadsVm, historyVm, settingsVm, aboutVm);

        var provider = new SimpleServiceProvider();
        provider.Register(settingsService);
        provider.Register(ytdlpService);
        provider.Register(cookieService);
        provider.Register(historyService);
        provider.Register(notificationService);
        provider.Register(updateService);
        provider.Register(metadataService);
        provider.Register(downloadService);
        provider.Register(queueService);
        provider.Register(mainVm);
        provider.Register(homeVm);
        provider.Register(downloadsVm);
        provider.Register(historyVm);
        provider.Register(settingsVm);
        provider.Register(aboutVm);

        Services = provider;
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        _mainWindow = new MainWindow();
        _mainWindow.Activate();
    }
}

public class SimpleServiceProvider : IServiceProvider
{
    private readonly Dictionary<Type, object> _services = new();

    public void Register<T>(T implementation) where T : notnull
    {
        _services[typeof(T)] = implementation;
    }

    public object? GetService(Type serviceType)
    {
        return _services.TryGetValue(serviceType, out var instance) ? instance : null;
    }
}

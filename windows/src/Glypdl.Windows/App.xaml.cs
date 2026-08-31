using Microsoft.UI.Xaml;
using Glypdl.Windows.Services;
using Glypdl.Windows.ViewModels;

namespace Glypdl.Windows;

public partial class App : Application
{
    public static MainWindow MainWindow { get; private set; } = null!;
    public static IServiceProvider Services { get; private set; } = null!;

    public App()
    {
        UnhandledException += (sender, e) =>
        {
            try
            {
                var dir = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Glypdl");
                System.IO.Directory.CreateDirectory(dir);
                System.IO.File.WriteAllText(System.IO.Path.Combine(dir, "crash_details.log"),
                    $"Message: {e.Message}\nException: {e.Exception}\nStackTrace:\n{e.Exception?.StackTrace}");
            }
            catch { }
        };

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

        var homeVm = new HomeViewModel(metadataService, queueService, settingsService, cookieService, historyService);
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

    public static void ApplyTheme(Models.AppTheme theme)
    {
        var elementTheme = theme switch
        {
            Models.AppTheme.Light => ElementTheme.Light,
            Models.AppTheme.Dark => ElementTheme.Dark,
            _ => ElementTheme.Default
        };

        if (MainWindow != null)
        {
            if (MainWindow.Content is FrameworkElement rootElement)
            {
                rootElement.RequestedTheme = elementTheme;
            }

            if (MainWindow.ContentFrame?.Content is FrameworkElement pageElement)
            {
                pageElement.RequestedTheme = elementTheme;
            }

            try
            {
                var titleBar = MainWindow.AppWindow?.TitleBar;
                if (titleBar != null)
                {
                    bool isDark = elementTheme == ElementTheme.Dark || 
                        (elementTheme == ElementTheme.Default && Current.RequestedTheme == ApplicationTheme.Dark);

                    titleBar.ButtonBackgroundColor = global::Windows.UI.Color.FromArgb(0, 0, 0, 0);
                    titleBar.ButtonInactiveBackgroundColor = global::Windows.UI.Color.FromArgb(0, 0, 0, 0);

                    if (isDark)
                    {
                        titleBar.ButtonForegroundColor = global::Windows.UI.Color.FromArgb(255, 255, 255, 255);
                        titleBar.ButtonHoverForegroundColor = global::Windows.UI.Color.FromArgb(255, 255, 255, 255);
                        titleBar.ButtonHoverBackgroundColor = global::Windows.UI.Color.FromArgb(35, 255, 255, 255);
                        titleBar.ButtonPressedForegroundColor = global::Windows.UI.Color.FromArgb(255, 255, 255, 255);
                        titleBar.ButtonPressedBackgroundColor = global::Windows.UI.Color.FromArgb(60, 255, 255, 255);
                        titleBar.ButtonInactiveForegroundColor = global::Windows.UI.Color.FromArgb(140, 255, 255, 255);
                    }
                    else
                    {
                        titleBar.ButtonForegroundColor = global::Windows.UI.Color.FromArgb(255, 0, 0, 0);
                        titleBar.ButtonHoverForegroundColor = global::Windows.UI.Color.FromArgb(255, 0, 0, 0);
                        titleBar.ButtonHoverBackgroundColor = global::Windows.UI.Color.FromArgb(35, 0, 0, 0);
                        titleBar.ButtonPressedForegroundColor = global::Windows.UI.Color.FromArgb(255, 0, 0, 0);
                        titleBar.ButtonPressedBackgroundColor = global::Windows.UI.Color.FromArgb(60, 0, 0, 0);
                        titleBar.ButtonInactiveForegroundColor = global::Windows.UI.Color.FromArgb(140, 0, 0, 0);
                    }
                }
            }
            catch { }
        }
    }

    public static void NavigateToHomeWithUrl(string url)
    {
        if (MainWindow != null)
        {
            MainWindow.NavigateToHome(url);
        }
    }

    public static void NavigateToDownloads()
    {
        if (MainWindow != null)
        {
            MainWindow.NavigateToDownloads();
        }
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        try
        {
            MainWindow = new MainWindow();

            Glypdl.Windows.Utilities.DispatcherHelper.Initialize(action =>
            {
                if (MainWindow.DispatcherQueue.HasThreadAccess)
                {
                    action();
                }
                else
                {
                    MainWindow.DispatcherQueue.TryEnqueue(() => action());
                }
            });

            MainWindow.Activate();

            var settingsService = (ISettingsService?)Services.GetService(typeof(ISettingsService));
            if (settingsService != null)
            {
                ApplyTheme(settingsService.GetSettings().Theme);
            }
        }
        catch (Exception ex)
        {
            var dir = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Glypdl");
            System.IO.Directory.CreateDirectory(dir);
            System.IO.File.WriteAllText(System.IO.Path.Combine(dir, "onlaunched_crash.log"), ex.ToString());
            throw;
        }
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

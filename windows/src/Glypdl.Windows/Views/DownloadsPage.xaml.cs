using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Glypdl.Windows.Models;
using Glypdl.Windows.Services;
using Glypdl.Windows.ViewModels;

namespace Glypdl.Windows.Views;

public partial class DownloadsPage : Page
{
    public DownloadsViewModel ViewModel { get; }

    public DownloadsPage()
    {
        InitializeComponent();
        ViewModel = (DownloadsViewModel)App.Services.GetService(typeof(DownloadsViewModel))!;
        DataContext = ViewModel;
        Loaded += (s, e) => ApplyCurrentTheme();
    }

    private void ApplyCurrentTheme()
    {
        var settingsService = (ISettingsService?)App.Services.GetService(typeof(ISettingsService));
        if (settingsService != null)
        {
            var theme = settingsService.GetSettings().Theme;
            RequestedTheme = theme switch
            {
                AppTheme.Light => ElementTheme.Light,
                AppTheme.Dark => ElementTheme.Dark,
                _ => ElementTheme.Default
            };
        }
    }

    public Visibility CountToEmptyVisibility(int count) => count == 0 ? Visibility.Visible : Visibility.Collapsed;
    public Visibility CountToListVisibility(int count) => count > 0 ? Visibility.Visible : Visibility.Collapsed;

    public Microsoft.UI.Xaml.Media.ImageSource? StringToImageSource(string? path)
    {
        if (string.IsNullOrWhiteSpace(path)) return null;
        try
        {
            if (Uri.TryCreate(path, UriKind.Absolute, out var uri))
            {
                return new Microsoft.UI.Xaml.Media.Imaging.BitmapImage(uri);
            }
            if (System.IO.File.Exists(path))
            {
                return new Microsoft.UI.Xaml.Media.Imaging.BitmapImage(new Uri(path));
            }
        }
        catch { }
        return null;
    }

    public Visibility IsActiveToVisibility(DownloadState state) =>
        (state == DownloadState.Downloading || state == DownloadState.Queued || state == DownloadState.FetchingInfo || state == DownloadState.Processing || state == DownloadState.Merging || state == DownloadState.Converting)
            ? Visibility.Visible : Visibility.Collapsed;

    public Visibility IsCompletedToVisibility(DownloadState state) =>
        state == DownloadState.Completed ? Visibility.Visible : Visibility.Collapsed;

    private void CancelButton_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { DataContext: DownloadItem item })
        {
            ViewModel.CancelDownload(item);
        }
    }

    private void PlayButton_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { DataContext: DownloadItem item })
        {
            if (!string.IsNullOrWhiteSpace(item.OutputPath) && System.IO.File.Exists(item.OutputPath))
            {
                try
                {
                    System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo { FileName = item.OutputPath, UseShellExecute = true });
                    return;
                }
                catch { }
            }

            var settings = (App.Services.GetService(typeof(ISettingsService)) as ISettingsService)?.GetSettings();
            var dir = !string.IsNullOrWhiteSpace(item.DownloadDirectory) && System.IO.Directory.Exists(item.DownloadDirectory)
                ? item.DownloadDirectory
                : (!string.IsNullOrWhiteSpace(settings?.DownloadDirectory) && System.IO.Directory.Exists(settings.DownloadDirectory)
                    ? settings.DownloadDirectory
                    : Utilities.PathUtils.GetDefaultDownloadDirectory());

            try
            {
                if (System.IO.Directory.Exists(dir))
                {
                    var files = System.IO.Directory.GetFiles(dir);
                    var cleanTitle = string.Concat(item.Title.Where(c => !System.IO.Path.GetInvalidFileNameChars().Contains(c))).Trim();
                    if (!string.IsNullOrWhiteSpace(cleanTitle))
                    {
                        var matched = files.FirstOrDefault(f =>
                            System.IO.Path.GetFileNameWithoutExtension(f).Contains(cleanTitle, StringComparison.OrdinalIgnoreCase) ||
                            cleanTitle.Contains(System.IO.Path.GetFileNameWithoutExtension(f), StringComparison.OrdinalIgnoreCase));
                        if (matched != null && System.IO.File.Exists(matched))
                        {
                            System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo { FileName = matched, UseShellExecute = true });
                            return;
                        }
                    }

                    var mediaExts = new[] { ".mp4", ".mkv", ".webm", ".mp3", ".m4a", ".opus", ".flac", ".wav", ".aac" };
                    var candidate = files.Where(f => mediaExts.Contains(System.IO.Path.GetExtension(f).ToLowerInvariant()))
                                         .OrderByDescending(f => System.IO.File.GetLastWriteTimeUtc(f))
                                         .FirstOrDefault();
                    if (candidate != null && System.IO.File.Exists(candidate))
                    {
                        System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo { FileName = candidate, UseShellExecute = true });
                    }
                }
            }
            catch { }
        }
    }

    private void OpenFolderButton_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { DataContext: DownloadItem item })
        {
            ViewModel.OpenFolder(item);
        }
    }
}

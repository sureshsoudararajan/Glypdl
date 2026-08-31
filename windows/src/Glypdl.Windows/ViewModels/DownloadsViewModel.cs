using System.Collections.ObjectModel;
using System.Diagnostics;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Glypdl.Windows.Models;
using Glypdl.Windows.Services;
using Glypdl.Windows.Utilities;

namespace Glypdl.Windows.ViewModels;

public partial class DownloadsViewModel : ObservableObject
{
    private readonly IQueueService _queueService;

    public ObservableCollection<DownloadItem> ActiveDownloads => _queueService.ActiveDownloads;
    public ObservableCollection<DownloadItem> QueuedDownloads => _queueService.QueuedDownloads;

    public DownloadsViewModel(IQueueService queueService)
    {
        _queueService = queueService;
    }

    [RelayCommand]
    public void CancelDownload(DownloadItem item)
    {
        _queueService.Cancel(item);
    }

    [RelayCommand]
    public void RetryDownload(DownloadItem item)
    {
        _queueService.Retry(item);
    }

    [RelayCommand]
    public void ClearCompleted()
    {
        _queueService.ClearCompleted();
    }

    [RelayCommand]
    public void OpenFile(DownloadItem item)
    {
        if (string.IsNullOrWhiteSpace(item.OutputPath) || !File.Exists(item.OutputPath)) return;
        try
        {
            Process.Start(new ProcessStartInfo
            {
                FileName = item.OutputPath,
                UseShellExecute = true
            });
        }
        catch { }
    }

    [RelayCommand]
    public void OpenFolder(DownloadItem item)
    {
        if (!string.IsNullOrWhiteSpace(item.OutputPath) && File.Exists(item.OutputPath))
        {
            try
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = "explorer.exe",
                    Arguments = $"/select,\"{item.OutputPath}\"",
                    UseShellExecute = true
                });
                return;
            }
            catch { }
        }

        var settings = (App.Services.GetService(typeof(ISettingsService)) as ISettingsService)?.GetSettings();
        string dir = !string.IsNullOrWhiteSpace(item.DownloadDirectory) && Directory.Exists(item.DownloadDirectory)
            ? item.DownloadDirectory
            : (!string.IsNullOrWhiteSpace(settings?.DownloadDirectory) && Directory.Exists(settings.DownloadDirectory)
                ? settings.DownloadDirectory
                : PathUtils.GetDefaultDownloadDirectory());

        try
        {
            if (Directory.Exists(dir))
            {
                var files = Directory.GetFiles(dir);
                var cleanTitle = string.Concat(item.Title.Where(c => !Path.GetInvalidFileNameChars().Contains(c))).Trim();
                if (!string.IsNullOrWhiteSpace(cleanTitle))
                {
                    var matched = files.FirstOrDefault(f =>
                        Path.GetFileNameWithoutExtension(f).Contains(cleanTitle, StringComparison.OrdinalIgnoreCase) ||
                        cleanTitle.Contains(Path.GetFileNameWithoutExtension(f), StringComparison.OrdinalIgnoreCase));
                    if (matched != null && File.Exists(matched))
                    {
                        Process.Start(new ProcessStartInfo
                        {
                            FileName = "explorer.exe",
                            Arguments = $"/select,\"{matched}\"",
                            UseShellExecute = true
                        });
                        return;
                    }
                }

                Process.Start(new ProcessStartInfo
                {
                    FileName = "explorer.exe",
                    Arguments = $"\"{dir}\"",
                    UseShellExecute = true
                });
            }
        }
        catch { }
    }
}

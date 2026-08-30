using System.Collections.ObjectModel;
using System.Diagnostics;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Glypdl.Windows.Models;
using Glypdl.Windows.Services;

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
        string dir = !string.IsNullOrWhiteSpace(item.DownloadDirectory) && Directory.Exists(item.DownloadDirectory)
            ? item.DownloadDirectory
            : Path.GetDirectoryName(item.OutputPath) ?? Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);

        try
        {
            Process.Start(new ProcessStartInfo
            {
                FileName = "explorer.exe",
                Arguments = $"\"{dir}\"",
                UseShellExecute = true
            });
        }
        catch { }
    }
}

using System.Collections.ObjectModel;
using System.Diagnostics;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Glypdl.Windows.Models;
using Glypdl.Windows.Services;

namespace Glypdl.Windows.ViewModels;

public partial class HistoryViewModel : ObservableObject
{
    private readonly IHistoryService _historyService;
    private readonly IQueueService _queueService;

    [ObservableProperty]
    private string _searchQuery = string.Empty;

    [ObservableProperty]
    private bool _isLoading;

    public ObservableCollection<HistoryEntry> HistoryEntries { get; } = new();

    public HistoryViewModel(IHistoryService historyService, IQueueService queueService)
    {
        _historyService = historyService;
        _queueService = queueService;
    }

    [RelayCommand]
    public async Task LoadHistoryAsync()
    {
        IsLoading = true;
        try
        {
            HistoryEntries.Clear();
            var entries = string.IsNullOrWhiteSpace(SearchQuery)
                ? await _historyService.GetAllAsync()
                : await _historyService.SearchAsync(SearchQuery.Trim());

            foreach (var e in entries)
            {
                HistoryEntries.Add(e);
            }
        }
        finally
        {
            IsLoading = false;
        }
    }

    [RelayCommand]
    public async Task DeleteEntryAsync(HistoryEntry entry)
    {
        await _historyService.DeleteEntryAsync(entry.Id);
        HistoryEntries.Remove(entry);
    }

    [RelayCommand]
    public async Task ClearAllHistoryAsync()
    {
        await _historyService.ClearAllAsync();
        HistoryEntries.Clear();
    }

    [RelayCommand]
    public void PlayMedia(HistoryEntry entry)
    {
        var file = entry.GetResolvedFilePath();
        if (string.IsNullOrWhiteSpace(file) || !File.Exists(file)) return;
        try
        {
            Process.Start(new ProcessStartInfo
            {
                FileName = file,
                UseShellExecute = true
            });
        }
        catch { }
    }

    [RelayCommand]
    public void OpenFolder(HistoryEntry entry)
    {
        string dir = Directory.Exists(entry.DownloadPath) ? entry.DownloadPath : Path.GetDirectoryName(entry.DownloadPath) ?? "";
        if (string.IsNullOrWhiteSpace(dir) || !Directory.Exists(dir)) return;

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

    [RelayCommand]
    public void DownloadAgain(HistoryEntry entry)
    {
        var item = new DownloadItem
        {
            Url = entry.Url,
            Title = entry.Title,
            Uploader = entry.Uploader,
            Duration = entry.Duration,
            Quality = entry.Quality,
            FormatId = entry.Format
        };
        _queueService.Enqueue(item);
    }
}

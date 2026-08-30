using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Glypdl.Windows.Models;
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
    }

    private void CancelButton_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { DataContext: DownloadItem item })
        {
            ViewModel.CancelDownload(item);
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

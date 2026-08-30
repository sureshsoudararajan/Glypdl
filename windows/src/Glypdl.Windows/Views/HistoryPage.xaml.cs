using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Glypdl.Windows.Models;
using Glypdl.Windows.ViewModels;

namespace Glypdl.Windows.Views;

public partial class HistoryPage : Page
{
    public HistoryViewModel ViewModel { get; }

    public HistoryPage()
    {
        InitializeComponent();
        ViewModel = (HistoryViewModel)App.Services.GetService(typeof(HistoryViewModel))!;
        DataContext = ViewModel;
        Loaded += (_, _) => _ = ViewModel.LoadHistoryAsync();
    }

    private void AutoSuggestBox_QuerySubmitted(AutoSuggestBox sender, AutoSuggestBoxQuerySubmittedEventArgs args)
    {
        _ = ViewModel.LoadHistoryAsync();
    }

    private void OpenFolderButton_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { DataContext: HistoryEntry entry })
        {
            ViewModel.OpenFolder(entry);
        }
    }

    private void DownloadAgainButton_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { DataContext: HistoryEntry entry })
        {
            ViewModel.DownloadAgain(entry);
        }
    }
}

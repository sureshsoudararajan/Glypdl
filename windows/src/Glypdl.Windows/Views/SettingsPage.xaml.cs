using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Glypdl.Windows.ViewModels;

namespace Glypdl.Windows.Views;

public partial class SettingsPage : Page
{
    public SettingsViewModel ViewModel { get; }

    public SettingsPage()
    {
        InitializeComponent();
        ViewModel = (SettingsViewModel)App.Services.GetService(typeof(SettingsViewModel))!;
        DataContext = ViewModel;
    }

    private async void ChangeFolder_Click(object sender, RoutedEventArgs e)
    {
        var picker = new Windows.Storage.Pickers.FolderPicker();
        picker.SuggestedStartLocation = Windows.Storage.Pickers.PickerLocationId.Downloads;
        picker.FileTypeFilter.Add("*");

        var folder = await picker.PickSingleFolderAsync();
        if (folder != null)
        {
            ViewModel.DownloadDirectory = folder.Path;
        }
    }
}

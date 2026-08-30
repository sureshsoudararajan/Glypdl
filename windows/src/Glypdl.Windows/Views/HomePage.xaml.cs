using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Glypdl.Windows.ViewModels;

namespace Glypdl.Windows.Views;

public partial class HomePage : Page
{
    public HomeViewModel ViewModel { get; }

    public HomePage()
    {
        InitializeComponent();
        ViewModel = (HomeViewModel)App.Services.GetService(typeof(HomeViewModel))!;
        DataContext = ViewModel;
    }

    private void UrlTextBox_KeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key == Windows.System.VirtualKey.Enter)
        {
            _ = ViewModel.FetchMetadataAsync();
        }
    }
}

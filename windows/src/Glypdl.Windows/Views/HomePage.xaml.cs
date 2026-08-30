using Microsoft.UI.Xaml;
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

    public static Visibility ObjectToVisibility(object? obj) => obj != null ? Visibility.Visible : Visibility.Collapsed;
    public static Visibility BoolToVisibility(bool value) => value ? Visibility.Visible : Visibility.Collapsed;
    public static Visibility StringToVisibility(string? str) => !string.IsNullOrWhiteSpace(str) ? Visibility.Visible : Visibility.Collapsed;

    private void UrlTextBox_KeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key == Windows.System.VirtualKey.Enter)
        {
            _ = ViewModel.FetchMetadataAsync();
        }
    }
}

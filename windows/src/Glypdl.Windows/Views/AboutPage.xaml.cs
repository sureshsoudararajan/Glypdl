using Microsoft.UI.Xaml.Controls;
using Glypdl.Windows.ViewModels;

namespace Glypdl.Windows.Views;

public partial class AboutPage : Page
{
    public AboutViewModel ViewModel { get; }

    public AboutPage()
    {
        InitializeComponent();
        ViewModel = (AboutViewModel)App.Services.GetService(typeof(AboutViewModel))!;
        DataContext = ViewModel;
        Loaded += (s, e) => ApplyCurrentTheme();
    }

    private void ApplyCurrentTheme()
    {
        var settingsService = (Services.ISettingsService?)App.Services.GetService(typeof(Services.ISettingsService));
        if (settingsService != null)
        {
            var theme = settingsService.GetSettings().Theme;
            RequestedTheme = theme switch
            {
                Models.AppTheme.Light => Microsoft.UI.Xaml.ElementTheme.Light,
                Models.AppTheme.Dark => Microsoft.UI.Xaml.ElementTheme.Dark,
                _ => Microsoft.UI.Xaml.ElementTheme.Default
            };
        }
    }
}

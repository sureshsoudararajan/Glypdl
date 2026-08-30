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
    }
}

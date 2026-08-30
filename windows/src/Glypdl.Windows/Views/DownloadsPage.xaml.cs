using Microsoft.UI.Xaml.Controls;
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
}

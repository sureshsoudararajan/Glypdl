using System;
using System.IO;
using Microsoft.UI.Xaml.Data;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;

namespace Glypdl.Windows.Converters;

public class StringToImageSourceConverter : IValueConverter
{
    public object? Convert(object? value, Type targetType, object? parameter, string? language)
    {
        if (value is not string path || string.IsNullOrWhiteSpace(path))
        {
            return null;
        }

        try
        {
            if (Uri.TryCreate(path, UriKind.Absolute, out var uri))
            {
                return new BitmapImage(uri);
            }
            if (File.Exists(path))
            {
                return new BitmapImage(new Uri(path));
            }
        }
        catch { }

        return null;
    }

    public object? ConvertBack(object? value, Type targetType, object? parameter, string? language)
    {
        throw new NotImplementedException("Not supported");
    }
}
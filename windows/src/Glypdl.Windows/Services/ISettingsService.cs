using Glypdl.Windows.Models;

namespace Glypdl.Windows.Services;

public interface ISettingsService
{
    AppSettings GetSettings();
    void SaveSettings(AppSettings settings);
}

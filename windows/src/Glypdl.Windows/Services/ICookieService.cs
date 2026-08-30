using Glypdl.Windows.Models;

namespace Glypdl.Windows.Services;

public interface ICookieService
{
    List<CookieProfile> GetProfiles();
    void AddProfile(string name, string filePath);
    void RemoveProfile(string profileId);
    CookieProfile? GetActiveProfile();
}

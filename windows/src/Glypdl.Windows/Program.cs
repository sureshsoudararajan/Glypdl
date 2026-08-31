using System;
using System.IO;
using System.Threading;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using WinRT;

namespace Glypdl.Windows;

public static class Program
{
    [STAThread]
    public static void Main(string[] args)
    {
        try
        {
            ComWrappersSupport.InitializeComWrappers();

            Application.Start((p) =>
            {
                var context = new DispatcherQueueSynchronizationContext(DispatcherQueue.GetForCurrentThread());
                SynchronizationContext.SetSynchronizationContext(context);
                new App();
            });
        }
        catch (Exception ex)
        {
            try
            {
                var logDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Glypdl");
                Directory.CreateDirectory(logDir);
                File.WriteAllText(Path.Combine(logDir, "startup_crash.log"), $"[{DateTime.Now}]\nException: {ex}\nInner: {ex.InnerException}\nStackTrace:\n{ex.StackTrace}\n");
            }
            catch { }
            throw;
        }
    }
}

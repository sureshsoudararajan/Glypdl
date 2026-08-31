namespace Glypdl.Windows.Utilities;

public static class DispatcherHelper
{
    private static Action<Action>? _uiDispatcher;

    public static void Initialize(Action<Action> uiDispatcher)
    {
        _uiDispatcher = uiDispatcher;
    }

    public static void ExecuteOnUIThread(Action action)
    {
        if (_uiDispatcher != null)
        {
            _uiDispatcher(action);
        }
        else
        {
            action();
        }
    }
}
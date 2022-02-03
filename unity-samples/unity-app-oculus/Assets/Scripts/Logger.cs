using System;
using UnityEngine;

public class Logger : IDisposable
{
    private readonly AndroidJavaObject _androidLogger;

    public Logger(string name)
    {
        using (var plugin = new AndroidJavaClass("org.slf4j.LoggerFactory"))
        {
            _androidLogger = plugin.CallStatic<AndroidJavaObject>("getLogger", name);
        }
    }

    public void Debug(string message)
    {
        _androidLogger.Call("debug", message);
    }

    public void Info(string message)
    {
        _androidLogger.Call("info", message);
    }
    
    public void Warn(string message)
    {
        _androidLogger.Call("warn", message);
    }

    public void Error(string message)
    {
        _androidLogger.Call("error", message);
    }

    public void Dispose()
    {
        _androidLogger.Dispose();
    }
}

using System;
using UnityEngine;

public class Log : IDisposable
{
    private readonly AndroidJavaObject androidJavaObject;

    public Log(string name)
    {
        using (var plugin = new AndroidJavaClass("org.slf4j.LoggerFactory"))
        {
            androidJavaObject = plugin.CallStatic<AndroidJavaObject>("getLogger", name);
        }
    }

    public void Debug(string mes)
    {
        androidJavaObject.Call("debug", mes);
    }

    public void Info(string mes)
    {
        androidJavaObject.Call("info", mes);
    }

    public void Warn(string mes)
    {
        androidJavaObject.Call("warning", mes);
    }

    public void Error(string mes)
    {
        androidJavaObject.Call("error", mes);
    }

    public void Dispose()
    {
        androidJavaObject?.Dispose();
    }
}


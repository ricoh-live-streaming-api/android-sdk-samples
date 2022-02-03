using System;
using System.IO;
using UnityEngine;

public class RTCStatsLogger : IDisposable
{
    private readonly string[] FILTERS = { 
        "candidate-pair", "outbound-rtp", "inbound-rtp",
        "remote-inbound-rtp", "track", 
        "sender", "media-source"
    };

    private StreamWriter streamWriter;

    public RTCStatsLogger(string path)
    {
        streamWriter = new StreamWriter(path);
    }

    public void Dispose()
    {
        streamWriter.Flush();
        streamWriter.Close();
    }

    public void Report(AndroidJavaObject unityPlayer, AndroidJavaObject reports)
    {
        streamWriter.Write(unityPlayer.Call<string>("toReportString", reports, FILTERS));
    }
}

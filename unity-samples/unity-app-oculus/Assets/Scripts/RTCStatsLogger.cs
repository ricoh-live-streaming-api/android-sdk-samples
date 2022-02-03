using System;
using System.Text;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using UnityEngine;

public class RTCStatsLogger : IDisposable
{
    private StreamWriter streamWriter;
    private static readonly string[] Filters = {
        "candidate-pair", "outbound-rtp", "inbound-rtp",
        "remote-inbound-rtp", "track",
        "sender", "media-source"
    };

    public RTCStatsLogger(string path)
    {
        streamWriter = new StreamWriter(path);
    }

    public void Report(AndroidJavaObject unityPlayer, AndroidJavaObject report)
    {
        streamWriter.Write(unityPlayer.Call<string>("toReportString", report, Filters));

    }

    public void Dispose()
    {
        streamWriter.Flush();
        streamWriter.Close();
    }
}

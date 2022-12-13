using System;
using System.Collections.Generic;
using UnityEngine;

public class RoomSpec
{
    public enum Type
    {
        SFU,
        SFU_LARGE,
        P2P,
        P2P_TURN,
    }

    private Type type;
    public RoomSpec(Type type)
    {
        this.type = type;
    }

    public AndroidJavaObject GetSpecHashMap()
    {
        var mediaControl = new Dictionary<string, object>
        {
            ["bitrate_reservation_mbps"] = 25
        };

        var dic = new Dictionary<string, object>
        {
            ["type"] = Enum.GetName(typeof(Type), type).ToLower(),
            ["media_control"] = mediaControl
        };
        return Utils.GetHashMap(dic);
    }
}

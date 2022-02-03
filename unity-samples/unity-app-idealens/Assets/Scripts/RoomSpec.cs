using System;
using System.Collections.Generic;
using UnityEngine;

public class RoomSpec
{
    public enum Type
    {
        SFU,
        P2P,
        P2P_TURN
    }

    private Type type;
    public RoomSpec(Type type)
    {
        this.type = type;
    }

    public AndroidJavaObject GetSpecHashMap()
    {
//        var mediaControl = new Dictionary<string, object>();
//        mediaControl["bitrate_reservation_mbps"] = 10;

        var dic = new Dictionary<string, object>();
        dic["type"] = Enum.GetName(typeof(Type), type).ToLower();
//        dic["media_control"] = mediaControl;
        return Utils.GetHashMap(dic);
    }
}

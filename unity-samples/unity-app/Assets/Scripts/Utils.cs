﻿
using System;
using System.Collections.Generic;
using System.IO;
using UnityEngine;
using UnityEngine.UI;

class Utils
{
    public static string CreateLogPath()
    {
        var path = Application.persistentDataPath + "/logs";
        var dt = DateTime.Now;
        var fileName = dt.ToString("yyyyMMdd'T'HHmm") + ".log";

        if (!Directory.Exists(path))
        {
            Directory.CreateDirectory(path);
        }

        return path + "/" + fileName;
    }

    public static Vector2 GetRectSize(GameObject gameObject)
    {
        RectTransform rt = gameObject.transform as RectTransform;
        return rt.sizeDelta;
    }

    public static void SetRectSize(GameObject gameObject, Vector2 size)
    {
        RectTransform rt = gameObject.transform as RectTransform;
        rt.sizeDelta = size;
    }

    public static void SetRectSize(RawImage rawImage, Vector2 size)
    {
        RectTransform rt = rawImage.transform as RectTransform;
        rt.sizeDelta = size;
    }

    public static void SetLocalPosition(GameObject gameObject, Vector2 position)
    {
        RectTransform rt = gameObject.transform as RectTransform;
        rt.localPosition = new Vector3(position.x, position.y);
    }

    public static AndroidJavaObject GetJavaObject(object val)
    {
        if (val is AndroidJavaObject)
        {
            return (AndroidJavaObject) val;
        }
        if (val is string)
        {
            AndroidJavaClass strClass = new AndroidJavaClass("java.lang.String");
            AndroidJavaObject strObj = strClass.CallStatic<AndroidJavaObject>("valueOf", val);
            return strObj;
        }
        else if (val is bool)
        {
            var booleanVal = new AndroidJavaObject("java.lang.Boolean", (bool)val);
            return booleanVal;
        }
        else if (val is int)
        {
            var integerVal = new AndroidJavaObject("java.lang.Integer", (int)val);
            return integerVal;
        }
        else if (val is long)
        {
            var longVal = new AndroidJavaObject("java.lang.Long", (long)val);
            return longVal;
        }
        else if (val is double)
        {
            var doubleVal = new AndroidJavaObject("java.lang.Double", (double)val);
            return doubleVal;
        }
        else if (val is float)
        {
            var floatVal = new AndroidJavaObject("java.lang.Float", (float)val);
            return floatVal;
        }
        else if (val is Dictionary<string, object>)
        {
            return GetHashMap((Dictionary<string, object>)val);
        }
        else
        {
            string str = val.ToString();
            AndroidJavaClass strClass = new AndroidJavaClass("java.lang.String");
            AndroidJavaObject strObj = strClass.CallStatic<AndroidJavaObject>("valueOf", str);
            return strObj;
        }
    }

    public static AndroidJavaObject GetHashMap(Dictionary<string, object> dict)
    {

        AndroidJavaObject hashMap = new AndroidJavaObject("java.util.HashMap");
        IntPtr putMethod = AndroidJNIHelper.GetMethodID(hashMap.GetRawClass(), "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
        foreach (KeyValuePair<string, object> entry in dict)
        {
            AndroidJavaObject javaObject = GetJavaObject(entry.Value);
            AndroidJNI.CallObjectMethod(
                hashMap.GetRawObject(),
                putMethod,
                AndroidJNIHelper.CreateJNIArgArray(new object[] { entry.Key, javaObject }));
        }

        return hashMap;
    }

    public static Dictionary<string, AndroidJavaObject> GetDictionaryFromHashMap(AndroidJavaObject hashmap)
    {
        var dict = new Dictionary<string, AndroidJavaObject>();
        AndroidJavaObject entrySet = hashmap.Call<AndroidJavaObject>("entrySet");
        AndroidJavaObject[] array = entrySet.Call<AndroidJavaObject[]>("toArray");
        foreach (AndroidJavaObject keyValuepair in array)
        {
            string key = keyValuepair.Call<string>("getKey");
            AndroidJavaObject value = keyValuepair.Call<AndroidJavaObject>("getValue");
            dict.Add(key, value);
        }

        return dict;
    }



#if UNITY_ANDROID
    /// <summary>
    /// Android用Toast表示
    /// </summary>
    /// <param name="message">表示するメッセージ</param>
    /// <param name="lengthLong">表示する長さ</param>
    public static void Toast(string message, bool lengthLong = true)
    {
        string duration = lengthLong ? "LENGTH_LONG" : "LENGTH_SHORT";

        // usingすると表示されなくなる
        AndroidJavaClass unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
        AndroidJavaObject activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity");
        AndroidJavaObject context = activity.Call<AndroidJavaObject>("getApplicationContext");

        activity.Call("runOnUiThread", new AndroidJavaRunnable(() => {
            AndroidJavaClass toastClass = new AndroidJavaClass("android.widget.Toast");
            AndroidJavaObject toast = toastClass.CallStatic<AndroidJavaObject>("makeText",
                                                  context,
                                                  message,
                                                  toastClass.GetStatic<int>(duration)
                                                  );
            toast.Call("show");
        }));
    }
#endif
    }

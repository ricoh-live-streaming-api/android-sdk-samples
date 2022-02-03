using System.Collections.Generic;
using UnityEngine;

public class CameraInfoList
{
    private List<string> cameraNameList = new List<string>();

    /// <summary>
    /// コンストラクタ
    /// </summary>
    /// <param name="unityplayer"></param>
    public CameraInfoList(AndroidJavaObject context)
    {
        using (AndroidJavaObject cameraManager = context.Call<AndroidJavaObject>("getSystemService", "camera"))
        {
            string[] cameraIdList = cameraManager.Call<string[]>("getCameraIdList");

            for (int i = 0; i < cameraIdList.Length; i++)
            {
                string cameraId = cameraIdList[i];
                using (AndroidJavaObject characteristics = cameraManager.Call<AndroidJavaObject>("getCameraCharacteristics", cameraId))
                using (AndroidJavaObject key_size = characteristics.GetStatic<AndroidJavaObject>("SENSOR_INFO_PIXEL_ARRAY_SIZE"))
                using (AndroidJavaObject key_facing = characteristics.GetStatic<AndroidJavaObject>("LENS_FACING"))
                using (AndroidJavaObject sizeObj = characteristics.Call<AndroidJavaObject>("get", key_size))
                using (AndroidJavaObject facingObj = characteristics.Call<AndroidJavaObject>("get", key_facing))
                {

                    var pixel = sizeObj.Call<int>("getHeight") * sizeObj.Call<int>("getWidth") / 1000 / 1000;

                    var facing = getLensFacing(facingObj.Call<int>("intValue"));

                    var info = string.Format("{0}({1}MP)", facing, pixel);

                    cameraNameList.Add(info);
                }
            }
        }
    }
    private string getLensFacing(int index)
    {
        switch (index)
        {
            case 0:// LENS_FACING_FRONT
                return "Front";
            case 1:// LENS_FACING_BACK
                return "Back";
            case 2:// LENS_FACING_EXTERNAL
                return "External";
            default:
                return "Unknown Camera";
        }
    }

    public string getName(int index)
    {
        return cameraNameList[index];
    }

    /// <summary>
    /// カメラの数を取得
    /// </summary>
    /// <returns>カメラの数</returns>
    public int getCameraNum()
    {
        return cameraNameList.Count;
    }
}


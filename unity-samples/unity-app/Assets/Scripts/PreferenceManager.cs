using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using UnityEngine;

public class PreferenceManager
{
    [Serializable]
    private class UserData
    {
        public string roomID;
    }

    private UserData userData;

    private string filePath;

    public PreferenceManager(string path)
    {
        filePath = string.Format("{0}/UserData.json", path);
        userData = new UserData();
    }

    public bool Save()
    {
        if (userData == null)
        {
            return false;
        }

        string json = JsonUtility.ToJson(userData);
        StreamWriter streamWriter = new StreamWriter(filePath);
        streamWriter.Write(json);
        streamWriter.Flush();
        streamWriter.Close();

        return true;
    }

    public bool Load()
    {
        if (!File.Exists(filePath))
        {
            return false;
        }

        StreamReader streamReader = new StreamReader(filePath);
        string data = streamReader.ReadToEnd();
        streamReader.Close();
        userData = JsonUtility.FromJson<UserData>(data);

        return true;
    }

    public string RoomID
    {
        set
        {
            if (userData != null)
            {
                userData.roomID = value;
                Save();
            }
        }
        get
        {
            return userData?.roomID;
        }
    }
}

using System.Collections;
using System.Collections.Generic;
using System.IO;
using UnityEngine;

public class UserDataManager
{
    [System.Serializable]
    private class UserData
    {
        // RoomID
        public string roomID;
    }

    private string filePath;
    private UserData userData;

    public UserDataManager(string path)
    {
        Debug.Log("***UserDataManager()");
        filePath = path + "/" + "UserData.json";
        Debug.Log("***filePath:" + filePath);
        userData = new UserData();
    }

    public void Save()
    {
        string json = JsonUtility.ToJson(userData);
        StreamWriter streamWriter = new StreamWriter(filePath);
        streamWriter.Write(json);
        streamWriter.Flush();
        streamWriter.Close();
    }
  
    public void Load()
    {
        if (File.Exists(filePath))
        {
            StreamReader streamReader;
            streamReader = new StreamReader(filePath);
            string data = streamReader.ReadToEnd();
            streamReader.Close();
            userData = JsonUtility.FromJson<UserData>(data);
            Debug.Log("***Load() RoomID:" + userData.roomID);
        }
    }

    public string RoomID
    {
        set
        {
            userData.roomID = value;
            Save();
        }
        get{ return userData.roomID; }
    }
}

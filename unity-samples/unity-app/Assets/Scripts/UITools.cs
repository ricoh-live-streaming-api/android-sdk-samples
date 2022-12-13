using System;
using System.Collections;
using System.Collections.Generic;
using System.Collections.Specialized;
using UnityEngine;
using UnityEngine.UI;

public class UITools : MonoBehaviour
{
    public static readonly string[] captureType =
    {
        "FHD",
        "4K",
    };

    [SerializeField]
    private GameObject remoteViewLayout = null;

    [SerializeField]
    private GameObject localRenderTarget = null;

    [SerializeField]
    private Dropdown dropdownCameraMode = null;

    [SerializeField]
    private Dropdown dropdownCaptureType = null;

    [SerializeField]
    private Button buttonConnection = null;

    [SerializeField]
    private InputField inputField_RoomID = null;

    [SerializeField]
    private YUVMaterialTexture localYUVTexture;

    [SerializeField]
    private Dropdown dropdownRoomType = null;

    /// <summary>
    /// 初期化処理
    /// </summary>
    /// <param name="context">Application Context</param>
    /// <param name="preferenceManager">Preference Manager</param>
    /// <param name="compositeInfo">合成フレーム情報</param>
    public void Initialize(AndroidJavaObject context, PreferenceManager preferenceManager, CompositeInfo compositeInfo)
    {
        var image = localRenderTarget.GetComponent<RawImage>();
        localYUVTexture = new YUVMaterialTexture(image, compositeInfo);
        localRenderTarget.SetActive(false);

        inputField_RoomID.text = string.IsNullOrEmpty(preferenceManager.RoomID) ? Secrets.ROOM_ID : preferenceManager.RoomID;

        // カメラ情報の登録
        var cameraListAdapter = new CameraInfoList(context);

        dropdownCameraMode.ClearOptions();
        dropdownCaptureType.ClearOptions();
        dropdownRoomType.ClearOptions();

        for (int i = 0; i < cameraListAdapter.getCameraNum(); i++)
        {
            dropdownCameraMode.options.Add(new Dropdown.OptionData(cameraListAdapter.getName(i)));
        }

        // キャプチャタイプの登録
        for (int i = 0; i < captureType.Length; i++)
        {
            dropdownCaptureType.options.Add(new Dropdown.OptionData(captureType[i]));
        }

        // RoomTypeの登録
        foreach (RoomSpec.Type type in Enum.GetValues(typeof(RoomSpec.Type)))
        {
            dropdownRoomType.options.Add(new Dropdown.OptionData(type.ToString()));
        }

        dropdownCameraMode.value = 0;
        dropdownCaptureType.value = 0;
        dropdownRoomType.value = 0;
    }

    public GameObject GetRemoteViewLayout()
    {
        return remoteViewLayout;
    }

    public GameObject GetLocalRenderTarget()
    {
        return localRenderTarget;
    }

    public YUVMaterialTexture GetYUVMaterialTexture()
    {
        return localYUVTexture;
    }

    public InputField GetInputField()
    {
        return inputField_RoomID;
    }

    public Dropdown GetDropdown_CameraMode()
    {
        return dropdownCameraMode;
    }

    public Dropdown GetDropdown_CaptureType()
    {
        return dropdownCaptureType;
    }

    public Button GetButton_Connection()
    {
        return buttonConnection;
    }

    public Dropdown GetDropdown_RoomType()
    {
        return dropdownRoomType;
    }
}

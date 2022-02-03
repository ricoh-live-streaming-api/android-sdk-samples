using System;
using System.Collections.Generic;
using System.Collections.Specialized;
using System.Threading;
using System.Threading.Tasks;
using UnityEngine;
using UnityEngine.UI;
using System.Linq;
using System.Timers;

public class Bidir : MonoBehaviour
{
    public GameObject baseContent;
    public GameObject remoteViewLayout;

    public Button buttonConnection;

    public InputField inputField_RoomID;

    public UITools[] uiTools;

    public Canvas[] canvas;

    private static readonly object frameLock = new object();

    private static readonly object lockObject = new object();

    private AndroidJavaObject unityPlugin = null;

    private AndroidJavaObject unityPlayer;

    private AndroidJavaObject context;

    private AndroidJavaObject client = null;

    private AndroidJavaObject capturer;

    private AndroidJavaObject renderLocalVideoTrack;

    private string localTrackId;

    private Dictionary<string, AndroidJavaObject> updateFrame = new Dictionary<string, AndroidJavaObject>();

    private OrderedDictionary remoteTracks = new OrderedDictionary();

    private PreferenceManager preferenceManager;

    private Log logger;
    private RTCStatsLogger statsLogger = null;

    private SynchronizationContext unityUIContext;
    private bool needUpdateRemoteViewLayout = false;

    private float displayedControllsTime = 0;

    private DeviceOrientation prevOrientation;

    private bool isConnected = false;

    private System.Timers.Timer statsTimer = null;
    private static readonly object statsLockObject = new object();

    // 合成フレームの情報
    // 合成した全体サイズ
    private const int CompositeFrameW = 1920;
    private const int CompositeFrameH = 3312;
    // 切り出し位置
    private const int CompositeCutTopLeftX = 0;
    private const int CompositeCutTopLeftY = 0;
    private const int CompositeCutBottomRightX = 1080;
    private const int CompositeCutButtomRightY = 720;


    private void Awake()
    {
        preferenceManager = new PreferenceManager(Application.persistentDataPath);
        preferenceManager.Load();

        logger = LoggerFactory.GetLogger(GetType().Name);
        logger.Debug("Bidir Scene Awake");
    }

    // Start is called before the first frame update
    void Start()
    {
        logger.Info("Bidir Scene Start.");

        unityUIContext = SynchronizationContext.Current;

        unityPlayer = new AndroidJavaObject("com.unity3d.player.UnityPlayer");

        context = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity").Call<AndroidJavaObject>("getApplicationContext");

        // ツールオブジェクトの初期化
        for (int i = 0; i < uiTools.Length; i++)
        {
            uiTools[i].Initialize(context, preferenceManager,
                                new CompositeInfo(
                    CompositeFrameW, CompositeFrameH,
                    CompositeCutTopLeftX, CompositeCutTopLeftY,
                    CompositeCutBottomRightX, CompositeCutButtomRightY));
        }

        prevOrientation = GetDeviceOrientation();
        ChangeOrientationProc();
        logger.Info("Bidir Scene Start(End).");
    }


    // Update is called once per frame
    void Update()
    {
        if (Input.GetKeyDown(KeyCode.Escape))
        {
            // バックキーが押された
            // アプリケーション終了
            Application.Quit();
            return;
        }

        // 画面の向きが変わった場合の処理
        DeviceOrientation current = GetDeviceOrientation();

        if (current != prevOrientation)
        {
            prevOrientation = current;
            ChangeOrientationProc();
        }

        if (needUpdateRemoteViewLayout)
        {
            needUpdateRemoteViewLayout = false;
            UpdateRemoteViewLayout(current);
        }


        lock (frameLock) {

            if (updateFrame.Count > 0)
            {
                foreach (var id in updateFrame.Keys)
                {
                    var frame = updateFrame[id];

                    using (var buffer = frame.Call<AndroidJavaObject>("getBuffer").Call<AndroidJavaObject>("toI420"))
                    {
                        int currentCanvas = GetCurrentCanvas();
                        // リモート用データ更新
                        if (remoteTracks.Contains(id))
                        {
                            var remoteView = (RemoteView) remoteTracks[id];
                            remoteView.YUVMaterialTexture.UpdateTexture(unityPlugin, buffer);
                            remoteView.Content.SetActive(true);
                        }

                        // ローカル用データ更新
                        if (renderLocalVideoTrack != null && localTrackId == id)
                        {
                            uiTools[currentCanvas].GetLocalRenderTarget().SetActive(true);
                            uiTools[currentCanvas].GetYUVMaterialTexture().UpdateTexture(unityPlugin, buffer);
                        }

                        buffer.Call("release");
                    }

                    frame.Call("release");
                    frame.Dispose();
                }

                updateFrame.Clear();
            }
        }

        // UIを特定時間で表示制御を行う
        if(isConnected)
        {
            displayedControllsTime += Time.deltaTime;

            if (IsTouchPanel())
            {
                SetDisplayUIActive(true);
                displayedControllsTime = 0;
            }
            else if(displayedControllsTime >= 5.0f)
            {
                SetDisplayUIActive(false);
            }
        }
        else
        {
            SetDisplayUIActive(true);
            displayedControllsTime = 0;
        }
    }

    /// <summary>
    /// 画面に触ったか
    /// </summary>
    /// <returns></returns>
    private bool IsTouchPanel()
    {
        if (Input.touchCount > 0)
        {
            return true;
        }

        return false;
    }

    /// <summary>
    /// UIの表示制御
    /// </summary>
    /// <param name="active">true:表示 false:非表示</param>
    private void SetDisplayUIActive(bool active)
    {
        int current = GetCurrentCanvas();

        // ルーム名エディタの表示制御
        inputField_RoomID.gameObject.SetActive(active);

        // カメラのドロップダウンの表示制御
        //dropdownCameraMode.gameObject.SetActive(active);
        uiTools[current].GetDropdown_CameraMode().gameObject.SetActive(active);

        // 解像度のドロップダウンの表示制御
        //dropdownCaptureType.gameObject.SetActive(active);
        uiTools[current].GetDropdown_CaptureType().gameObject.SetActive(active);

        // 接続ボタンの表示制御
        buttonConnection.gameObject.SetActive(active);

    }
    private int GetCurrentCanvas()
    {
        return (GetDeviceOrientation() == DeviceOrientation.Portrait) ? 0 : 1;
    }

    /// <summary>
    /// 画面の向きが変わった時の処理
    /// </summary>
    private void ChangeOrientationProc()
    {
        int current = GetCurrentCanvas();
        int prev = (current == 0) ? 1 : 0;

        // 情報をコピー
        uiTools[current].GetInputField().text = uiTools[prev].GetInputField().text;
        uiTools[current].GetInputField().gameObject.SetActive(uiTools[prev].GetInputField().gameObject.activeSelf);
        inputField_RoomID = uiTools[current].GetInputField();

        // カメラモードのリスト位置
        uiTools[current].GetDropdown_CameraMode().value = uiTools[prev].GetDropdown_CameraMode().value;
        uiTools[current].GetDropdown_CameraMode().gameObject.SetActive(uiTools[prev].GetDropdown_CameraMode().gameObject.activeSelf);

        // 解像度のリスト位置
        uiTools[current].GetDropdown_CaptureType().value = uiTools[prev].GetDropdown_CaptureType().value;
        uiTools[current].GetDropdown_CaptureType().gameObject.SetActive(uiTools[prev].GetDropdown_CaptureType().gameObject.activeSelf);

        // ボタン情報のコピー
        uiTools[current].GetButton_Connection().GetComponentInChildren<Text>().text =
            uiTools[prev].GetButton_Connection().GetComponentInChildren<Text>().text;
        uiTools[current].GetButton_Connection().interactable = uiTools[prev].GetButton_Connection().interactable;
        uiTools[current].GetButton_Connection().gameObject.SetActive(uiTools[prev].GetButton_Connection().gameObject.activeSelf);

        buttonConnection = uiTools[current].GetButton_Connection();

        UpdateRemoteViewLayout(GetDeviceOrientation(), true);

        canvas[current].enabled = true;
        canvas[prev].enabled = false;
    }

    /// <summary>
    /// 多拠点映像のレイアウト座標位置の更新を行う
    /// </summary>
    /// <param name="orientation"></param>
    /// <param name="isChangeParent"></param>
    private void UpdateRemoteViewLayout(DeviceOrientation orientation, bool isChangeParent = false)
    {
        int index = 0;
        var screenWidth = Screen.currentResolution.width;
        var screenHeight = Screen.currentResolution.height;
        int currentCanvas = GetCurrentCanvas();

        logger.Info(string.Format("screenWidth={0} screenHeight={1}", screenWidth, screenHeight));

        int count = remoteTracks.Count;     // 拠点数
        foreach (var trackId in remoteTracks.Keys)
        {
            var remoteView = (RemoteView) remoteTracks[trackId];
            var content = remoteView.Content;
            if (content.transform.parent == null || isChangeParent)
            {
                content.transform.SetParent(uiTools[currentCanvas].GetRemoteViewLayout().transform, false);
                content.SetActive(false);
            } else if (isChangeParent)
            {
                content.transform.SetParent(uiTools[currentCanvas].GetRemoteViewLayout().transform, false);
            }
            // 拠点数によって動的に配置位置を決定する
            // 中心が原点
            int width;
            int height;
            Vector2 pos;
            Vector2 rect;

            if (orientation == DeviceOrientation.Portrait)
            {
                if (count == 1)
                {
                    width = screenWidth;
                    height = screenHeight;
                }
                else if (count == 2)
                {
                    width = screenWidth;
                    height = screenHeight / 2;
                }
                else
                {
                    if ((count % 2) == 1 && index == count - 1)
                    {
                        width = screenWidth;
                    }
                    else
                    {
                        width = screenWidth / 2;
                    }

                    height = screenHeight / ((count + 1) / 2);
                }

                rect = new Vector2(width, height);
                pos = GetTopLeftPosition(screenWidth, screenHeight, rect);
                if (index == 0)
                {
                    // nothing to do.
                }
                else if (index == 1)
                {
                    if (count == 2)
                    {
                        pos.y -= screenHeight / 2;
                    }
                    else
                    {
                        pos.x += screenWidth / 2;
                    }
                }
                else
                {
                    if ((index % 2) == 1)
                    {
                        pos.x += screenWidth / 2;
                    }

                    pos.y -= rect.y * (((index - 2) / 2) + 1);
                }
            } else
            {
                if (count == 1)
                {
                    width = screenWidth;
                    height = screenHeight;
                }
                else if (count == 2)
                {
                    width = screenWidth / 2;
                    height = screenHeight;
                }
                else
                {
                    if ((count % 2) == 1 && index == count - 1)
                    {
                        height = screenHeight;
                    }
                    else
                    {
                        height = screenHeight / 2;
                    }

                    width = screenWidth / ((count + 1) / 2);
                }

                rect = new Vector2(width, height);
                pos = GetTopLeftPosition(screenWidth, screenHeight, rect);
                if (index == 0)
                {
                    // nothing to do.
                }
                else if (index == 1)
                {
                    if (count == 2)
                    {
                        pos.x += screenWidth / 2;
                    }
                    else
                    {
                        pos.y -= screenHeight / 2;
                    }
                }
                else
                {
                    if ((index % 2) == 1)
                    {
                        pos.y -= screenHeight / 2;
                    }

                    pos.x += rect.x * (((index - 2) / 2) + 1);
                }

            }

            remoteView.UpdatePosition(pos);
            remoteView.UpdateRectSize(rect);

            index++;
        }
    }

    /// <summary>
    /// rectから表示領域の左上の座標を取得する
    /// </summary>
    /// <param name="screenWidth">Screenの横幅</param>
    /// <param name="screenHeight">Screenの縦幅</param>
    /// <param name="rect">表示する画像表示のサイズ</param>
    /// <returns></returns>
    private Vector2 GetTopLeftPosition(int screenWidth, int screenHeight, Vector2 rect)
    {
        return new Vector2(-(screenWidth - rect.x) / 2, (screenHeight - rect.y) / 2);
    }

    /// <summary>
    /// アプリケーション終了時の処理
    /// </summary>
    private void OnApplicationQuit()
    {
        preferenceManager.RoomID = inputField_RoomID.text;
        preferenceManager.Save();
        logger.Dispose();
        statsLogger?.Dispose();
        unityPlugin?.Call("onDestroy");
    }

    /// <summary>
    /// デバイスの向きを取得
    /// </summary>
    /// <returns>デバイスの向き</returns>
    private DeviceOrientation GetDeviceOrientation()
    {
        if (Screen.width < Screen.height)
        {
            return DeviceOrientation.Portrait;
        }

        return DeviceOrientation.LandscapeLeft;
    }

    public void OnClickConnectButton()
    {
        logger.Debug("OnConnectButtonClick()");

        buttonConnection.interactable = false;

        if (!isConnected)
        {
            Connect();
        }
        else
        {
            Disconnect();
        }
    }

    private void Connect()
    {
        int current = GetCurrentCanvas();

        string roomID = inputField_RoomID.text;
        int cameraIndex = uiTools[current].GetDropdown_CameraMode().value;

        var task = Task.Run(() =>
        {
            lock (lockObject)
            {
                AndroidJNI.AttachCurrentThread();

                // Create UnityPlugin and Client.
                using (var supportCodecList = new AndroidJavaObject("java.util.ArrayList"))
                using (var codecUtils = new AndroidJavaClass("com.ricoh.livestreaming.webrtc.CodecUtils"))
                using (var vp8 = codecUtils.GetStatic<AndroidJavaObject>("VIDEO_CODEC_INFO_VP8"))
                using (var h264 = codecUtils.GetStatic<AndroidJavaObject>("VIDEO_CODEC_INFO_H264"))
                using (var h264HightProfile = codecUtils.GetStatic<AndroidJavaObject>("VIDEO_CODEC_INFO_H264_HIGH_PROFILE"))
                {
                    supportCodecList.Call<bool>("add", vp8);
                    supportCodecList.Call<bool>("add", h264);
                    supportCodecList.Call<bool>("add", h264HightProfile);

                    unityPlugin = new AndroidJavaObject("com.ricoh.livestreaming.unity.UnityPlugin", supportCodecList);
                }

                unityPlayer = new AndroidJavaObject("com.unity3d.player.UnityPlayer");

                context = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity").Call<AndroidJavaObject>("getApplicationContext");

                client = unityPlugin.Call<AndroidJavaObject>("getClient");
                client.Call("setEventListener", new ClientListener(this, unityPlugin));
                var str = unityPlugin.Call<string>("getStatus");
                logger.Info("#### 1 :" + str);
                try
                {
                    var capWidth = 0;
                    var capHeight = 0;
                    var videoBitrate = Secrets.VIDEO_BITRATE;

                    if (uiTools[current].GetDropdown_CaptureType().captionText.text == "4K")
                    {
                        capWidth = 3840;
                        capHeight = 2160;
                    }
                    else
                    {
                        capWidth = 1920;
                        capHeight = 1080;
                        videoBitrate /= 4;
                    }
                    capturer = new AndroidJavaObject("com.ricoh.livestreaming.webrtc.Camera2VideoCapturer",
                        context, cameraIndex.ToString(), capWidth, capHeight, 30);

                    var roomSpec = new RoomSpec(RoomSpec.Type.SFU);
                    var accessToken = JwtAccessToken.CreateAccessToken(Secrets.CLIENT_SECRET, inputField_RoomID.text, roomSpec);
                    var localLSTracks = CreateLocalMediaStreams();
                    using (var optionBuilder = new AndroidJavaObject("com.ricoh.livestreaming.Option$Builder"))
                    using (var videoCodec = new AndroidJavaClass("com.ricoh.livestreaming.SendingVideoOption$VideoCodecType"))
                    using (var h264 = videoCodec.GetStatic<AndroidJavaObject>("H264"))
                    using (var videoPriority = new AndroidJavaClass("com.ricoh.livestreaming.SendingVideoOption$SendingPriority"))
                    using (var sendingHigh = videoPriority.GetStatic<AndroidJavaObject>("HIGH"))
                    using (var sendingVideoOptionBuilder = new AndroidJavaObject("com.ricoh.livestreaming.SendingVideoOption$Builder"))
                    {
                        sendingVideoOptionBuilder.Call<AndroidJavaObject>("videoCodecType", h264);
                        sendingVideoOptionBuilder.Call<AndroidJavaObject>("sendingPriority", sendingHigh);
                        sendingVideoOptionBuilder.Call<AndroidJavaObject>("maxBitrateKbps", videoBitrate);
                        using (var sendingVideoOption = sendingVideoOptionBuilder.Call<AndroidJavaObject>("build"))
                        using (var sendingOption = new AndroidJavaObject("com.ricoh.livestreaming.SendingOption", sendingVideoOption))
                        {
                            optionBuilder.Call<AndroidJavaObject>("sending", sendingOption);

                            optionBuilder.Call<AndroidJavaObject>("localLSTracks", localLSTracks);
                            optionBuilder.Call<AndroidJavaObject>("meta",
                                Utils.GetJavaObject(new Dictionary<string, object>
                                    {
                                        {  "connect_meta", "unity_android" }
                                    })
                                );
                            using (var option = optionBuilder.Call<AndroidJavaObject>("build"))
                            {
                                client.Call("connect", Secrets.CLIENT_ID, accessToken, option);
                            }
                        }
                    }
               }
                catch (Exception e)
                {
                    logger.Error("Failed to connect." + e.Message);
                }
            }
        });
    }

    /// <summary>
    /// LocalMediaStreamの作成
    /// </summary>
    /// <returns>LocalMediaStreamのリスト</returns>
    private AndroidJavaObject CreateLocalMediaStreams()
    {
        var list = new AndroidJavaObject("java.util.ArrayList");
        // Create media stream
        using (var builder = new AndroidJavaObject("com.ricoh.livestreaming.MediaStreamConstraints$Builder"))
        {
            builder.Call<AndroidJavaObject>("videoCapturer", capturer);
            builder.Call<AndroidJavaObject>("audio", true);
            using (var constraints = builder.Call<AndroidJavaObject>("build"))
            using (var stream = client.Call<AndroidJavaObject>("getUserMedia", constraints))
            {
                using (var audioTracks = stream.Get<AndroidJavaObject>("audioTracks"))
                {
                    for (int i = 0; i < audioTracks.Call<int>("size"); i++)
                    {
                        using (var audioTrack = audioTracks.Call<AndroidJavaObject>("get", i))
                        using (var trackOptionBuilder = new AndroidJavaObject("com.ricoh.livestreaming.LSTrackOption$Builder"))
                        {
                            trackOptionBuilder.Call<AndroidJavaObject>("meta",
                                Utils.GetJavaObject(new Dictionary<string, object>
                                {
                                    {  "metasample", "unity_audio" }
                                }));

                            using (var option = trackOptionBuilder.Call<AndroidJavaObject>("build"))
                            {
                                var lsTrack = new AndroidJavaObject(
                                    "com.ricoh.livestreaming.LSTrack",
                                    audioTrack,
                                    stream,
                                    option
                                );
                                list.Call<bool>("add", lsTrack);
                            }
                        }

                    }
                }
                using (var videoTracks = stream.Get<AndroidJavaObject>("videoTracks"))
                {
                    for (int i = 0; i < videoTracks.Call<int>("size"); i++)
                    {
                        using (var videoTrack = videoTracks.Call<AndroidJavaObject>("get", i))
                        using (var trackOptionBuilder = new AndroidJavaObject("com.ricoh.livestreaming.LSTrackOption$Builder"))
                        {
                            trackOptionBuilder.Call<AndroidJavaObject>("meta",
                                Utils.GetJavaObject(new Dictionary<string, object>
                                {
                                    {  "metasample", "unity_video" }
                                }));

                            using (var option = trackOptionBuilder.Call<AndroidJavaObject>("build"))
                            {
                                var lsTrack = new AndroidJavaObject(
                                "com.ricoh.livestreaming.LSTrack",
                                videoTrack,
                                stream,
                                option);
                                list.Call<bool>("add", lsTrack);
                            }
                        }

                    }

                }
            }
        }
        return list;
    }

    private void Disconnect()
    {
        ClearVideoLayout();
        var task = Task.Run(() =>
        {
            lock (lockObject)
            {
                AndroidJNI.AttachCurrentThread();
                client.Call("disconnect");
            }
        });
    }

    private void RemoveRemoteView(string trackId)
    {
        if (remoteTracks.Contains(trackId))
        {
            var remoteView = (RemoteView)remoteTracks[trackId];

            Destroy(remoteView.Content);
            remoteTracks.Remove(trackId);
            // レイアウトの更新
            needUpdateRemoteViewLayout = true;
        }
    }

    /// <summary>
    /// 自拠点、相手拠点の映像レイアウトをクリアする
    /// </summary>
    private void ClearVideoLayout()
    {
        foreach (var view in remoteTracks.Values)
        {
            var remoteView = (RemoteView)view;
            Destroy(remoteView.Content);
        }

        remoteTracks.Clear();

        foreach (var uiTool in uiTools)
        {
            uiTool.GetLocalRenderTarget().SetActive(false);
        }

        renderLocalVideoTrack = null;
    }

    private class ClientListener : AndroidJavaProxy
    {
        private readonly Bidir app;
        private readonly AndroidJavaObject unityPlayer;

        /// <summary>
        /// コンストラクタ
        /// </summary>
        /// <param name="bidir"></param>
        /// <param name="unityPlayer"></param>
        public ClientListener(Bidir bidir, AndroidJavaObject unityPlayer) : base("com.ricoh.livestreaming.Client$Listener")
        {
            this.app = bidir;
            this.unityPlayer = unityPlayer;
        }

        /// <summary>
        /// 接続中の処理
        /// </summary>
        void onConnecting()
        {
            app.logger.Debug("ClientListener#OnConnecting");

            app.unityUIContext.Post(__ =>
            {
                // 接続ボタンの更新
                app.buttonConnection.GetComponentInChildren<Text>().text = "Connecting…";
                app.buttonConnection.interactable = false;
            }
            , null);
        }

        void onOpen()
        {
            app.logger.Debug("ClientListener#onOpen");

            app.unityUIContext.Post(__ =>
            {
                app.statsLogger = new RTCStatsLogger(Utils.CreateLogPath());
                app.capturer?.Call("start");

                // 接続ボタンの更新
                app.buttonConnection.GetComponentInChildren<Text>().text = "Disconnect";
                app.buttonConnection.interactable = true;
                app.isConnected = true;

                app.statsTimer = new System.Timers.Timer(1000);
                app.statsTimer.Elapsed += app.OnGetStatsTimedEvent;
                app.statsTimer.AutoReset = true;
                app.statsTimer.Enabled = true;
            }
            , null);
        }

        void onClosing()
        {
            app.logger.Debug("ClientListener#onClosing");

            app.unityUIContext.Post(__ =>
            {
                // 接続ボタンの更新
                app.buttonConnection.GetComponentInChildren<Text>().text = "Disconnecting…";
                app.buttonConnection.interactable = false;
            }
            , null);
        }

        void onClosed()
        {
            app.logger.Debug("ClientListener#onClosed");
            app.unityUIContext.Post(__ =>
            {
                // 接続ボタンの更新
                app.buttonConnection.GetComponentInChildren<Text>().text = "Connect";
                app.buttonConnection.interactable = true;


                if (app.statsTimer != null)
                {
                    app.statsTimer.Elapsed -= app.OnGetStatsTimedEvent;
                    app.statsTimer.Stop();
                    app.statsTimer.Dispose();
                    app.statsTimer = null;
                }

                lock (statsLockObject) {
                    app.statsLogger?.Dispose();
                    app.statsLogger = null;

                    app.capturer?.Call("stop");
                    app.capturer?.Call("release");

                    app.client?.Call("setEventListener", null);
                    app.client = null;

                    app.unityPlugin?.Call("onDestroy");
                    app.unityPlugin = null;
                }

                app.isConnected = false;
            }
             , null);
        }

        void onAddLocalTrack(AndroidJavaObject track, AndroidJavaObject stream)
        {
            app.logger.Debug(String.Format("ClientListener#onAddLocalTrack({0})", track.Call<string>("id")));

            app.unityUIContext.Post(__ =>
            {
                using (AndroidJavaClass cls = new AndroidJavaClass("org.webrtc.MediaStreamTrack"))
                {
                    if (track.Call<string>("kind") == cls.GetStatic<string>("VIDEO_TRACK_KIND"))
                    {
                        string id = track.Call<string>("id");
                        track.Call("addSink", new FrameUpdateListener(app, id));
                        app.renderLocalVideoTrack = track;
                        app.localTrackId = id;
                    }
                }
            }
            , null);
        }

        void onAddRemoteConnection(string connectionId, AndroidJavaObject metadata)
        {
            var dict = Utils.GetDictionaryFromHashMap(metadata);

            var metadataStr = "";
            foreach (var m in dict)
            {
                metadataStr += $"({m.Key}, {m.Value.Call<string>("toString")})";
            }
            app.logger.Debug(string.Format("ClientListener#onAddRemoteConnection(connectionId={0}, metadata={1})",
                connectionId, metadataStr));
        }

        void onRemoveRemoteConnection(string connectionId, AndroidJavaObject metadata, AndroidJavaObject mediaStreamTracks)
        {
            var dict = Utils.GetDictionaryFromHashMap(metadata);

            var metadataStr = "";
            foreach (var m in dict)
            {
                metadataStr += $"({m.Key}, {m.Value.Call<string>("toString")})";
            }
            app.logger.Debug(string.Format("ClientListener#onRemoveRemoteConnection(connectionId={0}, metadata={1})",
                connectionId, metadataStr));

            app.unityUIContext.Post(__ =>
            {
                app.RemoveRemoteTrackByConnectionId(connectionId);
            }
            , null);

        }

        void onAddRemoteTrack(string connectionId, AndroidJavaObject stream, AndroidJavaObject mediaStreamTrack, AndroidJavaObject metadata, AndroidJavaObject muteType)
        {
            var dict = Utils.GetDictionaryFromHashMap(metadata);

            var metadataStr = "";
            foreach (var m in dict)
            {
                metadataStr += $"({m.Key}, {m.Value.Call<string>("toString")})";
            }

            app.logger.Debug(String.Format("ClientListener#onAddRemoteTrack({0}, {1}, {2}, {3}, {4})",
                connectionId, stream.Call<string>("getId"), mediaStreamTrack.Call<string>("id"), metadataStr, muteType.Call<string>("toString")));

            app.unityUIContext.Post(__ =>
            {
                using(var cls = new AndroidJavaClass("org.webrtc.MediaStreamTrack"))
                {
                    if(mediaStreamTrack.Call<string>("kind") == cls.GetStatic<string>("VIDEO_TRACK_KIND"))
                    {
                        AndroidJavaObject isPodObject;
                        bool isPodUI = false;
                        if (dict.TryGetValue("isPod", out isPodObject))
                        {
                            isPodUI = isPodObject.Call<bool>("booleanValue");
                        }

                        string id = mediaStreamTrack.Call<string>("id");

                        mediaStreamTrack.Call("addSink", new FrameUpdateListener(app, id));

                        var content = Instantiate(app.baseContent, Vector3.zero, Quaternion.identity);
                        content.name = string.Format("track_{0}", id);

                        // すでにcoonectionIdと紐づいたViewがある場合は破棄する
                        app.RemoveRemoteTrackByConnectionId(connectionId);

                        app.remoteTracks.Add(id, new RemoteView(connectionId, id, content,
                            new CompositeInfo(
                                CompositeFrameW, CompositeFrameH,
                                CompositeCutTopLeftX, CompositeCutTopLeftY,
                                CompositeCutBottomRightX, CompositeCutButtomRightY),
                            isPodUI));
                        app.needUpdateRemoteViewLayout = true;
                    }
                }
            }
            , null);
        }

        void onUpdateRemoteConnection(string connectionId, AndroidJavaObject metadata)
        {
            var dict = Utils.GetDictionaryFromHashMap(metadata);

            var metadataStr = "";
            foreach (var m in dict)
            {
                metadataStr += $"({m.Key}, {m.Value.Call<string>("toString")})";
            }
            app.logger.Debug(string.Format("ClientListener#onUpdateRemoteConnection(connectionId={0}, metadata={1})",
                connectionId, metadataStr));
        }

        void onUpdateRemoteTrack(string connectionId, AndroidJavaObject stream, AndroidJavaObject mediaStreamTrack, AndroidJavaObject metadata)
        {
            var dict = Utils.GetDictionaryFromHashMap(metadata);

            var metadataStr = "";
            foreach (var m in dict)
            {
                metadataStr += $"({m.Key}, {m.Value.Call<string>("toString")})";
            }

            app.logger.Debug(String.Format("ClientListener#onUpdateRemoteTrack({0}, {1}, {2}, {3})",
                connectionId, stream.Call<string>("getId"), mediaStreamTrack.Call<string>("id"), metadataStr));
        }

        void onUpdateMute(string connectionId, AndroidJavaObject stream, AndroidJavaObject track, AndroidJavaObject muteType)
        {
            app.logger.Debug(String.Format("ClientListener#onUpdateMute({0}, {1}, {2}, {3})",
                connectionId, stream.Call<string>("getId"), track.Call<string>("id"), muteType.Call<string>("toString")));
        }

        void onChangeStability(string connectionId, AndroidJavaObject stability)
        {
            app.logger.Debug(String.Format("ClientListener#onChangeStability({0}, {1})",
                connectionId, stability.Call<string>("toString")));
        }

        void onError(AndroidJavaObject error)
        {
            app.logger.Error(string.Format("ClientListener#onError({0})", error.Call<string>("toReportString")));
        }
    }

    private void OnGetStatsTimedEvent(object sender, ElapsedEventArgs e) { 
        lock (statsLockObject)
        {
            if (client != null)
            {
                AndroidJNI.AttachCurrentThread();

                var stats = client?.Call<AndroidJavaObject>("getStats");
                statsLogger?.Report(unityPlugin, stats);
            }
        }
    }

    private void RemoveRemoteTrackByConnectionId(string connectionId)
    {
        var view = remoteTracks
            .Values
            .Cast<RemoteView>()
            .FirstOrDefault(t => t.ConnectionId == connectionId);
        if (view != null)
        {
            RemoveRemoteView(view.TrackId);
        }
    }

    public class FrameUpdateListener : AndroidJavaProxy
    {
        private readonly Bidir app;
        private readonly string id;

        public FrameUpdateListener(Bidir app, string id) : base("org.webrtc.VideoSink")
        {
            this.app = app;
            this.id = id;
        }

        void onFrame(AndroidJavaObject videoFrame)
        {
            lock (frameLock)
            {
                if (app.updateFrame.ContainsKey(id)) {
                    // Updateで描画されなかったフレームの破棄
                    var dropOldFrame = app.updateFrame[id];
                    dropOldFrame.Call("release");
                    dropOldFrame.Dispose();
                    app.updateFrame.Remove(id);
                }

                videoFrame.Call("retain");
                app.updateFrame.Add(id, videoFrame);
            }

        }
    }

}

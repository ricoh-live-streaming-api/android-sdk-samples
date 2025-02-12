using System;
using UnityEngine;
using UnityEngine.UI;
using System.Threading.Tasks;
using System.Threading;
using System.Timers;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using UnityEngine.Android;
using System.Collections;
using TMPro;
using UnityEngine.InputSystem;
using System.IO;

public class LiveStreaming : MonoBehaviour
{
    [SerializeField]
    private GameObject menuCanvas = null;
    [SerializeField]
    private GameObject buttonConnect = null;
    [SerializeField]
    private GameObject roomID = null;
    [SerializeField]
    private GameObject keyboard = null;
    [SerializeField]
    private GameObject equirectangularSphere = null;
    [SerializeField]
    private GameObject dualFisheyeSphere = null;
    [SerializeField]
    private RawImage normalVideoPlane = null;
    [SerializeField]
    private GameObject LController = null;
    [SerializeField]
    private GameObject RController = null;
    [SerializeField]
    private Material rgbaMaterial = null;
    [SerializeField]
    private TMP_Dropdown roomTypeDropDown = null;

    private SynchronizationContext unityUIContext;
    private AndroidJavaObject unityPlugin;
    private AndroidJavaObject unityPixelReader;
    private AndroidJavaObject client;
    static private object frameLock = new object();
    static private object lockObject = new object();
    AndroidJavaObject pendingFrame = null;
    private VideoRenderManager videoRenderManager;

    private TMP_InputField inputFieldRoomID;

    // Texture
    private Texture2D rgbaTexture;

    // Logger
    private Logger logger;
    private RTCStatsLogger statsLogger;
    private System.Timers.Timer statsTimer = null;

    private UserDataManager userDataManager;

    private bool isConnected = false;

    private float displayedControllsTime = 0;

    // default Quaternion of video spheres
    private Quaternion equirectangularSphereQuaternion;
    private Quaternion dualFisheyeSphereQuaternion;

    // Renderer
    private Renderer equirectangularRenderer;
    private Renderer dualFisheyeRenderer;

    private Vector2 normalPlaneVideoViewOriginalSize;
    private string libWebRTCLogDirPath;

    [DllImport("ls-client")]
    private static extern IntPtr getRenderEventFunc();

    void Awake()
    {
        userDataManager = new UserDataManager(Application.persistentDataPath);
        userDataManager.Load();

        logger = LoggerFactory.GetLogger(this.GetType().Name);
        videoRenderManager = new VideoRenderManager(new FrameUpdateListener(this));
    }

    // Start is called before the first frame update
    IEnumerator Start()
    {
        unityUIContext = SynchronizationContext.Current;
        keyboard.SetActive(false);

        // WebRTCライブラリで作成したTextureをUnityで利用するために必要な処理
        GL.IssuePluginEvent(getRenderEventFunc(), 0);

        // video plane is not displayed at first
        equirectangularSphere.SetActive(false);
        dualFisheyeSphere.SetActive(false);
        normalVideoPlane.gameObject.SetActive(false);
        equirectangularSphereQuaternion = equirectangularSphere.transform.localRotation;
        dualFisheyeSphereQuaternion = dualFisheyeSphere.transform.localRotation;

        inputFieldRoomID = roomID.GetComponent<TMP_InputField>();
        if (inputFieldRoomID != null)
        {
            // set previous value of RoomID
            // (get from Secrets when not saved)
            inputFieldRoomID.text = (string.IsNullOrEmpty(userDataManager.RoomID) ? Secrets.ROOM_ID : userDataManager.RoomID);
        }

        equirectangularRenderer = equirectangularSphere.GetComponent<Renderer>();
        dualFisheyeRenderer = dualFisheyeSphere.GetComponent<Renderer>();

        equirectangularRenderer.material = rgbaMaterial;
        dualFisheyeRenderer.material = rgbaMaterial;
        normalVideoPlane.material = rgbaMaterial;

        var rt = normalVideoPlane.transform as RectTransform;
        normalPlaneVideoViewOriginalSize = rt.sizeDelta;

        roomTypeDropDown.ClearOptions();
        foreach (RoomSpec.Type type in Enum.GetValues(typeof(RoomSpec.Type)))
        {
            roomTypeDropDown.options.Add(new TMP_Dropdown.OptionData(type.ToString()));
        }
        roomTypeDropDown.value = 0;
        roomTypeDropDown.RefreshShownValue();

        var logsDirPath = Application.persistentDataPath + "/logs";
        libWebRTCLogDirPath = logsDirPath + "/libwebrtc";
        if (!Directory.Exists(logsDirPath)) {
            Directory.CreateDirectory(logsDirPath);
        }
        if (!Directory.Exists(libWebRTCLogDirPath)) {
            Directory.CreateDirectory(libWebRTCLogDirPath);
        }

        if (!Permission.HasUserAuthorizedPermission(Permission.Microphone))
        {
            yield return RequestPermission(Permission.Microphone);
        }

        yield break;
    }

    IEnumerator RequestPermission(string permission)
    {
        logger.Info("RequestPermission " + permission);

        var callbacks = new PermissionCallbacks();
        callbacks.PermissionDenied += OnPermissionDenied;
        callbacks.PermissionGranted += OnPermissionGranted;
        callbacks.PermissionDeniedAndDontAskAgain += OnPermissionDeniedAndDontAskAgain;

        Permission.RequestUserPermission(permission, callbacks);

        yield break;
    }

    private void OnPermissionDeniedAndDontAskAgain(string permissionName)
    {
        logger.Warn("OnPermissionDeniedAndDontAskAgain");
        Application.Quit();
    }

    private void OnPermissionGranted(string permissName)
    {
        logger.Info("OnPermissionGranted");
    }

    private void OnPermissionDenied(string permissName)
    {
        logger.Info("OnPermissionDenied");
        Application.Quit();
    }

    public void OnApplicationQuit()
    {
        logger.Dispose();
        statsLogger?.Dispose();
        videoRenderManager.Clear();
        unityPixelReader?.Call("release");
        unityPixelReader?.Dispose();
        unityPlugin?.Call("onDestory");
        unityPlugin?.Dispose();
    }

    // Update is called once per frame
    void Update()
    {
        if (isConnected)
        {
            // add time of controls display time
            displayedControllsTime += Time.deltaTime;
        }

        lock (frameLock)
        {
            if (pendingFrame != null)
            {
                VideoRenderManager.VideoFormat videoFormat;

                try
                {
                    videoFormat = videoRenderManager.GetCurrentShowingTrackVideoFormat();

                    equirectangularSphere.SetActive(videoFormat == VideoRenderManager.VideoFormat.Equi);
                    dualFisheyeSphere.SetActive(videoFormat == VideoRenderManager.VideoFormat.DualFisheye);
                    normalVideoPlane.gameObject.SetActive(videoFormat == VideoRenderManager.VideoFormat.Normal);

                    using (AndroidJavaObject buffer = pendingFrame.Call<AndroidJavaObject>("getBuffer"))
                    {
                        int width = pendingFrame.Call<int>("getRotatedWidth");
                        int height = pendingFrame.Call<int>("getRotatedHeight");

                        if (rgbaTexture == null || rgbaTexture.width != width || rgbaTexture.height != height)
                        {
                            logger.Info(string.Format("create rgbaTexture texture. width={0}, height={1}", width, height));
                            CleanUp();
                            rgbaTexture = new Texture2D(width, height, TextureFormat.RGB24, false);
                            equirectangularRenderer.material.mainTexture = rgbaTexture;
                            dualFisheyeRenderer.material.mainTexture = rgbaTexture;
                            normalVideoPlane.texture = rgbaTexture;

                            AdjustAspectNormalVideoPlane(width, height);
                        }

                        var rgb888Buffer = unityPixelReader.Call<sbyte[]>("readPixelsAsync", pendingFrame);
                        rgbaTexture.LoadRawTextureData((byte[])(Array)rgb888Buffer);
                        rgbaTexture.Apply();
                    }
                }
                catch (Exception e)
                {
                    logger.Error(e.Message);
                }
                finally
                {
                    pendingFrame.Call("release");
                    pendingFrame.Dispose();
                    pendingFrame = null;
                }
            }
        }

        if (isConnected)
        {   // if no operation has been performed for a certain period of time during streaming, controls disappears
            // check if the controller is operated
            if (displayedControllsTime >= 5.0F)
            {
                // disappear controls
                SetMenuActive(false);
            }
        }
        else
        {
            // show controls while streaming is stopped
            SetMenuActive(true);
        }
    }

    /// <summary>
    /// normalVideoPlaneをアスペクト比に合わせた表示にする
    /// </summary>
    /// <param name="frameWidth">width</param>
    /// <param name="frameHeight">height</param>
    private void AdjustAspectNormalVideoPlane(int frameWidth, int frameHeight)
    {
        var textureSize = new Vector2(frameWidth, frameHeight);

        var heightScale = normalPlaneVideoViewOriginalSize.y / frameHeight;
        var widthScale = normalPlaneVideoViewOriginalSize.x / frameWidth;
        Vector2 rectSize = textureSize * Mathf.Min(heightScale, widthScale);

        Vector2 anchorDiff = normalVideoPlane.rectTransform.anchorMax - normalVideoPlane.rectTransform.anchorMin;
        Vector2 parentSize = (normalVideoPlane.transform.parent as RectTransform).rect.size;
        Vector2 anchorSize = parentSize * anchorDiff;

        normalVideoPlane.rectTransform.sizeDelta = rectSize - anchorSize;
    }

    /// <summary>
    /// 確保したTextureを解放する
    /// </summary>
    private void CleanUp()
    {
        if (rgbaTexture != null)
        {
            GameObject.Destroy(rgbaTexture);
            rgbaTexture = null;
        }
    }

    public void OnApplicationPause(bool isPaused)
    {
        logger.Info(string.Format("OnApplicationPause(isPaused={0}) is called.", isPaused));
        if (isPaused && isConnected)
        {
            // disconnect because application paused
            Disconnect();
        }
    }

    public void OnControllerPressed(InputAction.CallbackContext context)
    {
        if (context.action.phase == InputActionPhase.Performed && isConnected)
        {
            // if any button is pressed, reset display time and show controls
            displayedControllsTime = 0.0F;
            SetMenuActive(true);
        }
    }

    /// <summary>
    /// スティックボタンを左右に倒したときに呼ばれるイベントハンドラ
    /// </summary>
    public void OnMovePosition(InputAction.CallbackContext context)
    {
        if (context.action.phase == InputActionPhase.Performed)
        {
            if (equirectangularSphere.activeSelf || dualFisheyeSphere.activeSelf)
            {
                logger.Info("OnMovePosition");
                // rotate display direction(Equirectangular mode and DualFisheye mode)
                Vector2 inputMovement = context.ReadValue<Vector2>();
                if (inputMovement.x < 0)
                {
                    // rotate to left
                    equirectangularSphere.transform.Rotate(0, -18, 0);
                    dualFisheyeSphere.transform.Rotate(0, 18, 0);
                }
                else if (inputMovement.x > 0)
                {
                    // rotate to right
                    equirectangularSphere.transform.Rotate(0, 18, 0);
                    dualFisheyeSphere.transform.Rotate(0, -18, 0);
                }
            }
        }
    }

    /// <summary>
    /// スティックボタンを押し込んだときに呼ばれるイベントハンドラ
    /// </summary>
    public void OnInitilizeRotation(InputAction.CallbackContext context)
    {
        if (context.action.phase == InputActionPhase.Performed)
        {
            if (equirectangularSphere.activeSelf || dualFisheyeSphere.activeSelf)
            {
                // return to initial direction
                equirectangularSphere.transform.localRotation = equirectangularSphereQuaternion;
                dualFisheyeSphere.transform.localRotation = dualFisheyeSphereQuaternion;
            }
        }
    }

    /// <summary>
    /// 右スティックのBボタンを押したときに呼ばれるイベントハンドラ
    /// </summary>
    public void OnSwitchDisplay(InputAction.CallbackContext context)
    {
        if (context.action.phase == InputActionPhase.Performed)
        {
            logger.Info("OnSwitchDisplay");
            // Switch display participant.
            videoRenderManager.ToggleDisplay();
        }
    }


    public void OnConnectButtonClick()
    {
        if (!isConnected)
        {
            Connect();
        }
        else
        {
            Disconnect();
        }
    }

    public void OnRoomIDInputFieldClick()
    {
        if (!isConnected)
        {
            keyboard.SetActive(true);
        }
    }

    public void OnClearRoomIDButtonClick()
    {
        if (!isConnected)
        {
            // Restore from Secrets.
            userDataManager.RoomID = Secrets.ROOM_ID;
            inputFieldRoomID.text = Secrets.ROOM_ID;
        }
    }

    // display control of menu and controller
    private void SetMenuActive(bool isDisplay)
    {
        menuCanvas.SetActive(isDisplay);
        LController.SetActive(isDisplay);
        RController.SetActive(isDisplay);
    }

    private void Connect()
    {
        string roomId = inputFieldRoomID.text;
        userDataManager.RoomID = roomId;

        var task = Task.Run(() =>
        {
            lock (lockObject)
            {
                AndroidJNI.AttachCurrentThread();
                try
                {
                    // Create UnityPlugin and Client.
                    using (var supportCodecList = new AndroidJavaObject("java.util.ArrayList"))
                    using (var codecUtils = new AndroidJavaClass("com.ricoh.livestreaming.webrtc.CodecUtils"))
                    using (var h264 = codecUtils.GetStatic<AndroidJavaObject>("VIDEO_CODEC_INFO_H264"))
                    using (var h264HightProfile = codecUtils.GetStatic<AndroidJavaObject>("VIDEO_CODEC_INFO_H264_HIGH_PROFILE"))
                    {
                        supportCodecList.Call<bool>("add", h264);
                        supportCodecList.Call<bool>("add", h264HightProfile);

                        unityPlugin = new AndroidJavaObject("com.ricoh.livestreaming.unity.UnityPlugin", supportCodecList);
                    }
                    client = unityPlugin.Call<AndroidJavaObject>("getClient");
                    client.Call("setEventListener", new ClientListener(this));

                    using (var logOptionBuilder = new AndroidJavaObject("com.ricoh.livestreaming.LibWebrtcLogOption$Builder", libWebRTCLogDirPath))
                    {
                        logOptionBuilder.Call<AndroidJavaObject>("maxTotalFileSize", 4);
                        using (var logOption = logOptionBuilder.Call<AndroidJavaObject>("build")) 
                        {
                            client.Call("setLibWebrtcLogOption", logOption);
                        }
                    }

                    unityPixelReader = new AndroidJavaObject("com.ricoh.livestreaming.unity.UnityPixelReader", unityPlugin);

                    RoomSpec.Type roomType;
                    Enum.TryParse(roomTypeDropDown.captionText.text, out roomType);
                    logger.Info(string.Format("roomType={0}", roomType));
                    var roomSpec = new RoomSpec(roomType);
                    var accessToken = JwtAccessToken.CreateAccessToken(Secrets.CLIENT_SECRET, roomId, roomSpec);
                    var localLSTracks = CreateLocalLSTracks();
                    using (var optionBuilder = new AndroidJavaObject("com.ricoh.livestreaming.Option$Builder"))
                    {
                        optionBuilder.Call<AndroidJavaObject>("meta",
                            Utils.GetJavaObject(new Dictionary<string, object>
                                {
                                    {  "connect_meta", "oculus_quest" }
                                })
                            );
                        optionBuilder.Call<AndroidJavaObject>("localLSTracks", localLSTracks);
                        using (var option = optionBuilder.Call<AndroidJavaObject>("build"))
                        {
                            client.Call("connect", Secrets.CLIENT_ID, accessToken, option);
                        }
                    }
                }
                catch (Exception e)
                {
                    logger.Error("Failed to connect. message=" + e.Message);
                }
            }
        });
    }

    /// <summary>
    /// LocalLSTrackの作成
    /// </summary>
    /// <returns>LocalLSTrackのリスト</returns>
    private AndroidJavaObject CreateLocalLSTracks()
    {
        var list = new AndroidJavaObject("java.util.ArrayList");
        using (var builder = new AndroidJavaObject("com.ricoh.livestreaming.MediaStreamConstraints$Builder"))
        {
            // OculusQuestはカメラが使用できないのでAudioのみ作成する
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
                                    {  "metasample", "audio" }
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
            }
        }
        return list;
    }

    private void Disconnect()
    {
        var task = Task.Run(() =>
        {
            lock (lockObject)
            {
                AndroidJNI.AttachCurrentThread();
                client?.Call("disconnect");
            }
        });
    }

    class ClientListener : AndroidJavaProxy
    {
        private readonly LiveStreaming app;

        public ClientListener(LiveStreaming liveStreaming) : base("com.ricoh.livestreaming.Client$Listener")
        {
            this.app = liveStreaming;
        }


        void onConnecting(AndroidJavaObject connectingEvent)
        {
            app.logger.Debug("ClientListener#OnConnecting");

            app.unityUIContext.Post(__ =>
            {
                var button = app.buttonConnect.GetComponent<Button>();
                button.GetComponentInChildren<TMP_Text>().text = "Connecting...";
                button.interactable = false;
                app.inputFieldRoomID.interactable = false;
                app.roomTypeDropDown.interactable = false;
            }
             , null);
        }

        void onOpen(AndroidJavaObject openEvent)
        {
            app.logger.Debug("ClientListener#OnOpen");

            lock(lockObject)
            {
                app.isConnected = true;
                app.displayedControllsTime = 0;
                app.statsLogger = new RTCStatsLogger(Utils.CreateStatsLogPath());

                app.statsTimer = new System.Timers.Timer(1000);
                app.statsTimer.Elapsed += app.OnGetStatsTimedEvent;
                app.statsTimer.AutoReset = true;
                app.statsTimer.Enabled = true;
            }

            app.unityUIContext.Post(__ =>
            {
                var button = app.buttonConnect.GetComponent<Button>();
                button.GetComponentInChildren<TMP_Text>().text = "Disconnect";
                button.interactable = true;
            }
             , null);
        }

        void onClosing(AndroidJavaObject closingEvent)
        {
            app.logger.Debug("ClientListener#onClosing");

            app.unityUIContext.Post(__ =>
            {
                var button = app.buttonConnect.GetComponent<Button>();
                button.GetComponentInChildren<TMP_Text>().text = "Disconnecting...";
                button.interactable = false;
            }
             , null);
        }

        void onClosed(AndroidJavaObject closedEvent)
        {
            app.logger.Debug("ClientListener#onClosed");

            lock (lockObject)
            {
                if (app.statsTimer != null)
                {
                    app.statsTimer.Elapsed -= app.OnGetStatsTimedEvent;
                    app.statsTimer.Stop();
                    app.statsTimer.Dispose();
                    app.statsTimer = null;
                }

                app.statsLogger?.Dispose();
                app.statsLogger = null;

                app.client?.Call("setEventListener", (AndroidJavaObject) null);
                app.client?.Dispose();
                app.client = null;

                app.unityPixelReader?.Call("release");
                app.unityPixelReader?.Dispose();
                app.unityPlugin?.Call("onDestroy");
                app.unityPlugin?.Dispose();
                app.unityPlugin = null;
                app.isConnected = false;
                app.videoRenderManager.Clear();
            }

            app.unityUIContext.Post(__ =>
            {
                var button = app.buttonConnect.GetComponent<Button>();
                button.GetComponentInChildren<TMP_Text>().text = "Connect";
                button.interactable = true;
                app.inputFieldRoomID.interactable = true;
                app.roomTypeDropDown.interactable = true;

                app.equirectangularSphere.SetActive(false);
                app.dualFisheyeSphere.SetActive(false);
                app.normalVideoPlane.gameObject.SetActive(false);

                app.CleanUp();
            }
             , null);
        }

        void onAddLocalTrack(AndroidJavaObject addLocalEvent)
        {
            using (var track = addLocalEvent.Call<AndroidJavaObject>("getMediaStreamTrack"))
            {
                app.logger.Debug(string.Format("ClientListener#onAddLocalTrack({0})", track.Call<string>("id")));
            }
        }

        void onAddRemoteConnection(AndroidJavaObject addRemoteConnectionEvent)
        {

            var metadataStr = "";
            using (var meta = addRemoteConnectionEvent.Call<AndroidJavaObject>("getMeta"))
            {
                var dict = Utils.GetDictionaryFromHashMap(meta);

                foreach (var m in dict)
                {
                    if (m.Value != null)
                    {
                        metadataStr += $"({m.Key}, {m.Value.Call<string>("toString")})";
                    }
                }
            }
            app.logger.Debug(string.Format("ClientListener#onAddRemoteConnection(connectionId={0}, metadata={1})",
                addRemoteConnectionEvent.Call<string>("getConnectionId"), metadataStr));
        }

        void onRemoveRemoteConnection(AndroidJavaObject removeRemoteConnectionEvent)
        {
            var connectionId = removeRemoteConnectionEvent.Call<string>("getConnectionId");
            var metadataStr = "";
            using (var meta = removeRemoteConnectionEvent.Call<AndroidJavaObject>("getMeta"))
            {
                var dict = Utils.GetDictionaryFromHashMap(meta);

                foreach (var m in dict)
                {
                    if (m.Value != null)
                    {
                        metadataStr += $"({m.Key}, {m.Value.Call<string>("toString")})";
                    }
                }
            }
            app.logger.Debug(string.Format("ClientListener#onRemoveRemoteConnection(connectionId={0}, metadata={1})",
                connectionId, metadataStr));

            app.videoRenderManager.RemoveRemoteTrack(connectionId);
            if (app.videoRenderManager.GetNumOfRemoteTrack() == 0)
            {
                app.unityUIContext.Post(__ =>
                {
                    app.equirectangularSphere.SetActive(false);
                    app.dualFisheyeSphere.SetActive(false);
                    app.normalVideoPlane.gameObject.SetActive(false);

                    app.CleanUp();
                }
                 , null);
            }
        }

        void onAddRemoteTrack(AndroidJavaObject addRemoteTrackEvent)
        {
            var connectionId = addRemoteTrackEvent.Call<string>("getConnectionId");
            var metadataStr = "";
            using (var meta = addRemoteTrackEvent.Call<AndroidJavaObject>("getMeta"))
            using (var stream = addRemoteTrackEvent.Call<AndroidJavaObject>("getStream"))
            using (var muteType = addRemoteTrackEvent.Call<AndroidJavaObject>("getMute"))
            {
                var dict = Utils.GetDictionaryFromHashMap(meta);

                foreach (var m in dict)
                {
                    if (m.Value != null)
                    {
                        metadataStr += $"({m.Key}, {m.Value.Call<string>("toString")})";
                    }
                }
                var mediaStreamTrack = addRemoteTrackEvent.Call<AndroidJavaObject>("getMediaStreamTrack");

                app.logger.Debug(string.Format("ClientListener#onAddRemoteTrack({0}, {1}, {2}, {3}, {4})",
                    connectionId, stream.Call<string>("getId"), mediaStreamTrack.Call<string>("id"), metadataStr, muteType.Call<string>("toString")));

                using (AndroidJavaClass cls = new AndroidJavaClass("org.webrtc.MediaStreamTrack"))
                {
                    if (mediaStreamTrack.Call<string>("kind") == cls.GetStatic<string>("VIDEO_TRACK_KIND"))
                    {
                        app.videoRenderManager.AddRemoteTrack(connectionId, mediaStreamTrack, app.GetVideoFormat(dict));
                    }
                }
            }
        }

        void onUpdateRemoteConnection(AndroidJavaObject updateRemoteConnectionEvent)
        {
            var connectionId = updateRemoteConnectionEvent.Call<string>("getConnectionId");
            var metadataStr = "";
            using (var meta = updateRemoteConnectionEvent.Call<AndroidJavaObject>("getMeta"))
            {
                var dict = Utils.GetDictionaryFromHashMap(meta);

                foreach (var m in dict)
                {
                    if (m.Value != null)
                    {
                        metadataStr += $"({m.Key}, {m.Value.Call<string>("toString")})";
                    }
                }
            }

            app.logger.Debug(string.Format("ClientListener#onUpdateRemoteConnection({0}, {1})",
                connectionId, metadataStr));
        }

        void onUpdateRemoteTrack(AndroidJavaObject updateRemoteTrackEvent)
        {
            var connectionId = updateRemoteTrackEvent.Call<string>("getConnectionId");
            using (var meta = updateRemoteTrackEvent.Call<AndroidJavaObject>("getMeta"))
            using (var stream = updateRemoteTrackEvent.Call<AndroidJavaObject>("getStream"))
            using (var mediaStreamTrack = updateRemoteTrackEvent.Call<AndroidJavaObject>("getMediaStreamTrack"))
            {
                var metadataStr = "";
                var dict = Utils.GetDictionaryFromHashMap(meta);

                foreach (var m in dict)
                {
                    if (m.Value != null)
                    {
                        metadataStr += $"({m.Key}, {m.Value.Call<string>("toString")})";
                    }
                }

                app.logger.Debug(string.Format("ClientListener#onUpdateRemoteTrack({0}, {1}, {2}, {3})",
                    connectionId, stream.Call<string>("getId"), mediaStreamTrack.Call<string>("id"), metadataStr));
            }
        }

        void onUpdateMute(AndroidJavaObject updateMuteEvent)
        {
            var connectionId = updateMuteEvent.Call<string>("getConnectionId");
            using (var muteType = updateMuteEvent.Call<AndroidJavaObject>("getMute"))
            using (var stream = updateMuteEvent.Call<AndroidJavaObject>("getStream"))
            using (var mediaStreamTrack = updateMuteEvent.Call<AndroidJavaObject>("getMediaStreamTrack")) 
            {
                app.logger.Debug(string.Format("ClientListener#onUpdateMute({0}, {1}, {2}, {3})",
                    connectionId, stream.Call<string>("getId"), mediaStreamTrack.Call<string>("id"), muteType.Call<string>("toString")));
            }
        }

        void onChangeStability(AndroidJavaObject chaneStabilityEvent)
        {
            var connectionId = chaneStabilityEvent.Call<string>("getConnectionId");
            using (var stability = chaneStabilityEvent.Call<AndroidJavaObject>("getStability"))
            {
                app.logger.Debug(String.Format("ClientListener#onChangeStability({0}, {1})",
                    connectionId, stability.Call<string>("toString")));
            }
        }

        void onError(AndroidJavaObject error)
        {
            app.logger.Error(string.Format("ClientListener#onError({0})",
                error.Call<string>("toReportString")));
        }
    }

    /// <summary>
    /// メタデータからVideoFormatを取得する
    /// </summary>
    /// <param name="meta">メタデータ</param>
    /// <returns>ビデオフォーマット</returns>
    private VideoRenderManager.VideoFormat GetVideoFormat(Dictionary<string, AndroidJavaObject> meta)
    {
        AndroidJavaObject isTheta;
        AndroidJavaObject thetaVideoFormat;
        if (meta.TryGetValue("isTheta", out isTheta))
        {
            if (isTheta.Call<bool>("booleanValue"))
            {

                if (meta.TryGetValue("thetaVideoFormat", out thetaVideoFormat))
                {
                    var format = thetaVideoFormat.Call<string>("toString");

                    if (format == "eq")
                    {
                        return VideoRenderManager.VideoFormat.Equi;
                    }
                    else if (format == "dl")
                    {
                        return VideoRenderManager.VideoFormat.DualFisheye;
                    }
                }

                // isThetaがtrueの場合、デフォルトはequirectangular
                return VideoRenderManager.VideoFormat.Equi;
            }
        }

        return VideoRenderManager.VideoFormat.Normal;
    }

    private void OnGetStatsTimedEvent(object sender, ElapsedEventArgs e)
    {
        lock (lockObject)
        {
            if (client != null)
            {
                AndroidJNI.AttachCurrentThread();

                var stats = client?.Call<AndroidJavaObject>("getStats");
                statsLogger?.Report(unityPlugin, stats);
            }
        }
    }


    public class FrameUpdateListener : AndroidJavaProxy
    {
        private readonly LiveStreaming liveStreaming;

        public FrameUpdateListener(LiveStreaming liveStreaming) : base("org.webrtc.VideoSink")
        {
            this.liveStreaming = liveStreaming;
        }

        void onFrame(AndroidJavaObject videoFrame)
        {
            lock (frameLock)
            {
                bool dropOldFrame = (liveStreaming.pendingFrame != null);
                if (dropOldFrame)
                {
                    liveStreaming.pendingFrame.Call("release");
                    liveStreaming.pendingFrame.Dispose();
                }
                liveStreaming.pendingFrame = videoFrame;
                liveStreaming.pendingFrame.Call("retain");
            }
        }
    }
}

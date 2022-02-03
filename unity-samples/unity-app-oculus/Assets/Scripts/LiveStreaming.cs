using System;
using UnityEngine;
using UnityEngine.UI;
using System.IO;
using System.Threading.Tasks;
using System.Threading;
using System.Timers;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using UnityEngine.Android;
using System.Collections;

public class LiveStreaming : MonoBehaviour
{
    [SerializeField]
    private GameObject uiHelpersToInstantiate = null;
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
    private GameObject normalVideoPlane = null;
    [SerializeField]
    private GameObject LController = null;
    [SerializeField]
    private GameObject RController = null;
    [SerializeField]
    private Material videoMaterial = null;
    [SerializeField]
    private Material yuvVideoMaterial = null;

    private SynchronizationContext unityUIContext;
    private AndroidJavaObject unityPlugin;
    private AndroidJavaObject client;
    static private object frameLock = new object();
    static private object lockObject = new object();
    AndroidJavaObject pendingFrame = null;
    private VideoRenderManager videoRenderManager;

    private InputField inputFieldRoomID;

    // Texture
    private Texture2D mainTex;
    private Texture2D uTex;
    private Texture2D vTex;
    private Texture2D nativeTexture;
    private RenderTexture renderTexture;

    // Buffer
    private byte[] yBuffer;
    private byte[] uBuffer;
    private byte[] vBuffer;
    private byte[] pixelBuffer;

    // Logger
    private Logger logger;
    private RTCStatsLogger statsLogger;
    private System.Timers.Timer statsTimer = null;

    private LaserPointer laserPointer;

    public LaserPointer.LaserBeamBehavior laserBeamBehavior;

    private UserDataManager userDataManager;

    private bool isConnected = false;

    private float displayedControllsTime = 0;

    // default Quaternion of video spheres
    private Quaternion equirectangularSphereQuaternion;
    private Quaternion dualFisheyeSphereQuaternion;

    // Renderer
    private Renderer equirectangularRenderer;
    private Renderer dualFisheyeRenderer;
    private Renderer normalVideoRenderer;

    private bool isShowYUVVideo = true;

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
        normalVideoPlane.SetActive(false);
        equirectangularSphereQuaternion = equirectangularSphere.transform.localRotation;
        dualFisheyeSphereQuaternion = dualFisheyeSphere.transform.localRotation;

        inputFieldRoomID = roomID.GetComponent<InputField>();
        if (inputFieldRoomID != null)
        {
            // set previous value of RoomID
            // (get from Secrets when not saved)
            inputFieldRoomID.text = (string.IsNullOrEmpty(userDataManager.RoomID) ? Secrets.ROOM_ID : userDataManager.RoomID);
        }

        equirectangularRenderer = equirectangularSphere.GetComponent<Renderer>();
        dualFisheyeRenderer = dualFisheyeSphere.GetComponent<Renderer>();
        normalVideoRenderer = normalVideoPlane.GetComponent<Renderer>();

        // setup Texture
        mainTex = new Texture2D(2, 2);
        mainTex.SetPixel(0, 0, Color.blue);
        mainTex.SetPixel(1, 1, Color.blue);
        mainTex.Apply();
        equirectangularRenderer.material.mainTexture = mainTex;
        dualFisheyeRenderer.material.mainTexture = mainTex;
        normalVideoRenderer.material.mainTexture = mainTex;

        uTex = new Texture2D(2, 2);
        uTex.SetPixel(0, 0, Color.blue);
        uTex.SetPixel(1, 1, Color.blue);
        uTex.Apply();
        equirectangularRenderer.material.SetTexture("_UTex", uTex);
        dualFisheyeRenderer.material.SetTexture("_UTex", uTex);
        normalVideoRenderer.material.SetTexture("_UTex", uTex);

        vTex = new Texture2D(2, 2);
        vTex.SetPixel(0, 0, Color.blue);
        vTex.SetPixel(1, 1, Color.blue);
        vTex.Apply();
        equirectangularRenderer.material.SetTexture("_VTex", vTex);
        dualFisheyeRenderer.material.SetTexture("_VTex", vTex);
        normalVideoRenderer.material.SetTexture("_VTex", vTex);

        // setup UIHelper
        if (uiHelpersToInstantiate)
        {
            GameObject.Instantiate(uiHelpersToInstantiate);
        }

        // setup LaserPointer
        laserPointer = FindObjectOfType<LaserPointer>();
        if (!laserPointer)
        {
            logger.Error("Debug UI requires use of a LaserPointer and will not function without it. Add one to your scene, or assign the UIHelpers prefab to the DebugUIBuilder in the inspector.");
            yield break;
        }
        laserPointer.laserBeamBehavior = LaserPointer.LaserBeamBehavior.On;

        // add HMD unmounted listener
        OVRManager.HMDUnmounted += HMDUnmounted;

        if (!Permission.HasUserAuthorizedPermission(Permission.Microphone))
        {
            yield return RequestPermission(Permission.Microphone);

            if (!Permission.HasUserAuthorizedPermission(Permission.Microphone)) {
                // パーミッションの許可がされなかったためアプリを終了する
                logger.Error("Microphone permission is not granted.");
                Application.Quit();
            }
        }

        yield break;
    }

    IEnumerator RequestPermission(string permission)
    {
        logger.Info("RequestPermission " + permission);

        Permission.RequestUserPermission(permission);

        yield break;
    }


    public void OnApplicationQuit()
    {
        logger.Dispose();
        statsLogger?.Dispose();
        videoRenderManager.Clear();
        unityPlugin?.Call("onDestory");
    }

    // Update is called once per frame
    void Update()
    {
        if (isConnected)
        {
            // add time of controls display time
            displayedControllsTime += Time.deltaTime;

            if (!OVRManager.hasInputFocus)
            {
                logger.Debug("***OVRManager.hasInputFocus is false");
                // disconnect because application focus lost
                Disconnect();
            }
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
                    normalVideoPlane.SetActive(videoFormat == VideoRenderManager.VideoFormat.Normal);

                    using (AndroidJavaObject buffer = pendingFrame.Call<AndroidJavaObject>("getBuffer"))
                    {
                        int width = buffer.Call<int>("getWidth");
                        int height = buffer.Call<int>("getHeight");

                        if (IsTextureBuffer(buffer))
                        {
                            // TextureBufferの場合はWebRTCライブラリ内でTextureにVideoFrameが描画される
                            // Unity側ではそのTextureを利用してMaterialに反映する

                            if (isShowYUVVideo)
                            {
                                // materialを切り替える
                                isShowYUVVideo = false;
                                equirectangularRenderer.material = videoMaterial;
                                dualFisheyeRenderer.material = videoMaterial;
                                normalVideoRenderer.material = videoMaterial;
                            }

                            int textureName = buffer.Call<int>("getTextureId");
                            IntPtr textureId = new IntPtr(textureName);

                            if (nativeTexture == null || nativeTexture.width != width || nativeTexture.height != height)
                            {
                                logger.Info(string.Format("create native texture. width={0}, height={1}", width, height));
                                CleanUp();
                                nativeTexture = Texture2D.CreateExternalTexture(width, height, TextureFormat.RGBA32, false, false, textureId);
                                equirectangularRenderer.material.mainTexture = nativeTexture;
                                dualFisheyeRenderer.material.mainTexture = nativeTexture;
                                normalVideoRenderer.material.mainTexture = nativeTexture;
                            }
                            else
                            {
                                nativeTexture.UpdateExternalTexture(textureId);
                            }
                        }
                        else
                        {
                            if (!isShowYUVVideo)
                            {
                                // materialを切り替える
                                isShowYUVVideo = true;
                                equirectangularRenderer.material = yuvVideoMaterial;
                                dualFisheyeRenderer.material = yuvVideoMaterial;
                                normalVideoRenderer.material = yuvVideoMaterial;

                                SetTextureToMaterial(equirectangularRenderer.material, mainTex, uTex, vTex);
                                SetTextureToMaterial(dualFisheyeRenderer.material, mainTex, uTex, vTex);
                                SetTextureToMaterial(normalVideoRenderer.material, mainTex, uTex, vTex);
                            }

                            using (AndroidJavaObject i420Buffer = buffer.Call<AndroidJavaObject>("toI420"))
                            {
                                int chromaWidth = width / 2;
                                int chromaHeight = height / 2;

                                // Y-Plane
                                if (mainTex.width != width || mainTex.height != height || mainTex.format != TextureFormat.Alpha8)
                                {
                                    mainTex = new Texture2D(width, height, TextureFormat.Alpha8, false);
                                    yBuffer = new byte[width * height];
                                    equirectangularRenderer.material.mainTexture = mainTex;
                                    dualFisheyeRenderer.material.mainTexture = mainTex;
                                    normalVideoRenderer.material.mainTexture = mainTex;
                                }
                                UploadPlaneDataToTexture(
                                    unityPlugin,
                                    i420Buffer,
                                    "copyYPlaneData",
                                    yBuffer,
                                    width * height,
                                    mainTex);

                                // U-Plane
                                if (uTex.width != chromaWidth || uTex.height != chromaHeight)
                                {
                                    uTex = new Texture2D(chromaWidth, chromaHeight, TextureFormat.Alpha8, false);
                                    uBuffer = new byte[chromaWidth * chromaHeight];
                                    equirectangularRenderer.material.SetTexture("_UTex", uTex);
                                    dualFisheyeRenderer.material.SetTexture("_UTex", uTex);
                                    normalVideoRenderer.material.SetTexture("_UTex", uTex);
                                }
                                UploadPlaneDataToTexture(
                                    unityPlugin,
                                    i420Buffer,
                                    "copyUPlaneData",
                                    uBuffer,
                                    chromaWidth * chromaHeight,
                                    uTex);

                                // V-Plane
                                if (vTex.width != chromaWidth || vTex.height != chromaHeight)
                                {
                                    vTex = new Texture2D(chromaWidth, chromaHeight, TextureFormat.Alpha8, false);
                                    vBuffer = new byte[chromaWidth * chromaHeight];
                                    equirectangularRenderer.material.SetTexture("_VTex", vTex);
                                    dualFisheyeRenderer.material.SetTexture("_VTex", vTex);
                                    normalVideoRenderer.material.SetTexture("_VTex", vTex);
                                }
                                UploadPlaneDataToTexture(
                                    unityPlugin,
                                    i420Buffer,
                                    "copyVPlaneData",
                                    vBuffer,
                                    chromaWidth * chromaHeight,
                                    vTex);

                                i420Buffer.Call("release");
                            }
                        }
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

        if (equirectangularSphere.activeSelf || dualFisheyeSphere.activeSelf)
        {
            // rotate display direction(Equirectangular mode and DualFisheye mode)
            if (OVRInput.GetDown(OVRInput.RawButton.LThumbstickLeft | OVRInput.RawButton.RThumbstickLeft))
            {
                // rotate to left
                equirectangularSphere.transform.Rotate(0, -18, 0);
                dualFisheyeSphere.transform.Rotate(0, 18, 0);
            }
            else if (OVRInput.GetDown(OVRInput.RawButton.LThumbstickRight | OVRInput.RawButton.RThumbstickRight))
            {
                // rotate to right
                equirectangularSphere.transform.Rotate(0, 18, 0);
                dualFisheyeSphere.transform.Rotate(0, -18, 0);
            }
            else if (OVRInput.GetDown(OVRInput.RawButton.LThumbstick | OVRInput.RawButton.RThumbstick))
            {
                // return to initial direction
                equirectangularSphere.transform.localRotation = equirectangularSphereQuaternion;
                dualFisheyeSphere.transform.localRotation = dualFisheyeSphereQuaternion;
            }
        }

        if (OVRInput.GetDown(OVRInput.Button.Two))
        {
            // Switch display participant.
            videoRenderManager.ToggleDisplay();
        }

        if (isConnected)
        {   // if no operation has been performed for a certain period of time during streaming, controls disappears
            // check if the controller is operated
            if (OVRInput.GetDown(OVRInput.RawButton.Any, OVRInput.Controller.All))
            {
                // if any button is pressed, reset display time and show controls
                displayedControllsTime = 0.0F;
                SetMenuActive(true);
            }
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
    /// 確保したTextureを解放する
    /// </summary>
    private void CleanUp()
    {
        if (nativeTexture != null)
        {
            GameObject.Destroy(nativeTexture);
            nativeTexture = null;
        }

        if (renderTexture != null)
        {
            renderTexture.Release();
            GameObject.Destroy(renderTexture);
            renderTexture = null;
        }

    }

    /// <summary>
    /// TextureBufferかどうかのチェック
    /// </summary>
    /// <param name="buffer">VideoFrameのBuffer</param>
    /// <returns>true: TextureBuffer</returns>
    private bool IsTextureBuffer(AndroidJavaObject buffer)
    {
        try
        {
            buffer.Call<int>("getTextureId");
            return true;
        }
        catch
        {
            return false;
        }
    }

    /// <summary>
    /// MaterialにYUVTextureをセットする
    /// </summary>
    /// <param name="material">Material</param>
    /// <param name="yTexture">Y Texture</param>
    /// <param name="uTexture">U Texture</param>
    /// <param name="vTexture">V Texture</param>
    private void SetTextureToMaterial(Material material, Texture2D yTexture, Texture2D uTexture, Texture2D vTexture)
    {
        material.mainTexture = yTexture;
        material.SetTexture("_UTex", uTexture);
        material.SetTexture("_VTex", vTexture);
    }

    private void HMDUnmounted()
    {
        logger.Info("HMDUnmounted() is called.");
        if (isConnected)
        {
            // disconnect because HMD unmounted
            Disconnect();
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


    /// <summary>
    /// Textureへ画像データをアップデートする
    /// </summary>
    /// <param name="unityPlugin">java層のUnityPluginクラスへのアクセスが可能なオブジェクト</param>
    /// <param name="yuvPlaneData">YUVデータが格納されたバッファ</param>
    /// <param name="methodName">java層のUnityPluginクラスの呼び出し先メソッド名</param>
    /// <param name="dstBuffer">Planeデータの書き込み先。このデータがTextureに転送される</param>
    /// <param name="size">書き込みサイズ</param>
    /// <param name="texture">書き込み先のTexture</param>
    private void UploadPlaneDataToTexture(
        AndroidJavaObject unityPlugin,
        AndroidJavaObject yuvPlaneData,
        string methodName,
        byte[] dstBuffer,
        int size,
        Texture2D texture)
    {
        var handle = default(GCHandle);
        try
        {
            handle = GCHandle.Alloc(dstBuffer, GCHandleType.Pinned);
            var ptr = handle.AddrOfPinnedObject();

            unityPlugin.Call(methodName, yuvPlaneData, ptr.ToInt32(), 0, size);
            texture.LoadRawTextureData(dstBuffer);
            texture.Apply();
        }
        finally
        {
            if (handle != default(GCHandle))
            {
                handle.Free();
            }
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
        laserPointer.laserBeamBehavior = (isDisplay ? LaserPointer.LaserBeamBehavior.On : LaserPointer.LaserBeamBehavior.Off);
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

                    var roomSpec = new RoomSpec(RoomSpec.Type.SFU);
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


        void onConnecting()
        {
            app.logger.Debug("ClientListener#OnConnecting");

            app.unityUIContext.Post(__ =>
            {
                var button = app.buttonConnect.GetComponent<Button>();
                button.GetComponentInChildren<Text>().text = "Connecting...";
                button.interactable = false;
                app.inputFieldRoomID.interactable = false;
            }
             , null);
        }

        void onOpen()
        {
            app.logger.Debug("ClientListener#OnOpen");

            lock(lockObject)
            {
                app.isConnected = true;
                app.displayedControllsTime = 0;
                app.statsLogger = new RTCStatsLogger(Utils.CreateLogPath());

                app.statsTimer = new System.Timers.Timer(1000);
                app.statsTimer.Elapsed += app.OnGetStatsTimedEvent;
                app.statsTimer.AutoReset = true;
                app.statsTimer.Enabled = true;
            }

            app.unityUIContext.Post(__ =>
            {
                var button = app.buttonConnect.GetComponent<Button>();
                button.GetComponentInChildren<Text>().text = "Disconnect";
                button.interactable = true;
            }
             , null);
        }

        void onClosing()
        {
            app.logger.Debug("ClientListener#onClosing");

            app.unityUIContext.Post(__ =>
            {
                var button = app.buttonConnect.GetComponent<Button>();
                button.GetComponentInChildren<Text>().text = "Disconnecting...";
                button.interactable = false;
            }
             , null);
        }

        void onClosed()
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

                app.client?.Call("setEventListener", null);
                app.client = null;

                app.unityPlugin?.Call("onDestroy");
                app.unityPlugin = null;
                app.isConnected = false;
                app.videoRenderManager.Clear();
            }

            app.unityUIContext.Post(__ =>
            {
                var button = app.buttonConnect.GetComponent<Button>();
                button.GetComponentInChildren<Text>().text = "Connect";
                button.interactable = true;
                app.inputFieldRoomID.interactable = true;

                app.equirectangularSphere.SetActive(false);
                app.dualFisheyeSphere.SetActive(false);
                app.normalVideoPlane.SetActive(false);

                app.CleanUp();
            }
             , null);
        }

        void onAddLocalTrack(AndroidJavaObject track, AndroidJavaObject stream)
        {
            app.logger.Debug(string.Format("ClientListener#onAddLocalTrack({0})", track.Call<string>("id")));
        }

        void onAddRemoteConnection(string connectionId, AndroidJavaObject metadata)
        {
            var dict = Utils.GetDictionaryFromHashMap(metadata);

            var metadataStr = "";
            foreach (var m in dict)
            {
                if (m.Value != null) {
                    metadataStr += $"({m.Key}, {m.Value.Call<string>("toString")})";
                }
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
                if (m.Value != null)
                {
                    metadataStr += $"({m.Key}, {m.Value.Call<string>("toString")})";
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
                    app.normalVideoPlane.SetActive(false);

                    app.CleanUp();
                }
                 , null);
            }
        }

        void onAddRemoteTrack(string connectionId, AndroidJavaObject stream, AndroidJavaObject mediaStreamTrack, AndroidJavaObject metadata, AndroidJavaObject muteType)
        {
            var dict = Utils.GetDictionaryFromHashMap(metadata);

            var metadataStr = "";
            foreach (var m in dict)
            {
                if (m.Value != null)
                {
                    metadataStr += $"({m.Key}, {m.Value.Call<string>("toString")})";
                }
            }

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

        void onUpdateRemoteConnection(string connectionId, AndroidJavaObject metadata)
        {
            var dict = Utils.GetDictionaryFromHashMap(metadata);

            var metadataStr = "";
            foreach (var m in dict)
            {
                if (m.Value != null)
                {
                    metadataStr += $"({m.Key}, {m.Value.Call<string>("toString")})";
                }
            }

            app.logger.Debug(string.Format("ClientListener#onUpdateRemoteConnection({0}, {1})",
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

            app.logger.Debug(string.Format("ClientListener#onUpdateRemoteTrack({0}, {1}, {2}, {3})",
                connectionId, stream.Call<string>("getId"), mediaStreamTrack.Call<string>("id"), metadataStr));
        }

        void onUpdateMute(string connectionId, AndroidJavaObject stream, AndroidJavaObject track, AndroidJavaObject muteType)
        {
            app.logger.Debug(string.Format("ClientListener#onUpdateMute({0}, {1}, {2}, {3})",
                connectionId, stream.Call<string>("getId"), track.Call<string>("id"), muteType.Call<string>("toString")));
        }

        void onChangeStability(string connectionId, AndroidJavaObject stability)
        {
            app.logger.Debug(String.Format("ClientListener#onChangeStability({0}, {1})",
                connectionId, stability.Call<string>("toString")));
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

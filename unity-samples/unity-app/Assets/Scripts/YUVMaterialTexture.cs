using System;
using System.Runtime.InteropServices;
using UnityEngine;
using UnityEngine.UI;

public class YUVMaterialTexture
{
    private Texture2D mainTex = null;
    private Texture2D uTex = null;
    private Texture2D vTex = null;
    private readonly RawImage rawImage;
    private Log logger;
    private float frameAspectRatio = 1.0f;
    private float drawnAspectRatio = 1.0f;
    private int frameWidth = 0;
    private int frameHeight = 0;
    private readonly CompositeInfo compositeInfo;
    private byte[] yBuffer;
    private byte[] uBuffer;
    private byte[] vBuffer;
    private readonly bool isPodUI;

    /// <summary>
    /// コンストラクタ
    /// </summary>
    /// <param name="image">表示先RawImage</param>
    /// <param name="compositeInfo">合成フレーム情報</param>
    /// <param name="isPodUI">VR Pod映像かどうか</param>
    public YUVMaterialTexture(RawImage image, CompositeInfo compositeInfo, bool isPodUI = false)
    {
        logger = LoggerFactory.GetLogger(GetType().Name);
        logger.Info(string.Format("Create YUVMaterialTexture(isPodUI={0})", isPodUI));

        this.compositeInfo = compositeInfo;
        this.isPodUI = isPodUI;
        rawImage = image;
        var shader = Shader.Find("UI/YUVVideoShader");
        rawImage.material = new Material(shader);
        RectTransform rt = rawImage.transform as RectTransform;
        drawnAspectRatio = rt.sizeDelta.x / rt.sizeDelta.y;
    }

    public void UpdateTexture(
        AndroidJavaObject unityPlayer, AndroidJavaObject buffer)
    {
        frameWidth = buffer.Call<int>("getWidth");
        frameHeight = buffer.Call<int>("getHeight");
        int offsetY = 0;

        int validFrameHeight = frameHeight;

        if (isPodUI)
        {
            // 合成データ
            // Shader内でYUV->RGB変換を行っているので、widthはフレームのサイズそのままを転送する
            // widthをそのまま渡さないと、YデータとUVデータの関係が崩れるため映像崩れが発生してしまう
            // heightをそのまま渡さないのは少しでも転送サイズを抑えるため、有効データのみTextureに転送する
            validFrameHeight = compositeInfo.CutBottomRightY - compositeInfo.CutTopLeftY;
            offsetY = compositeInfo.CutTopLeftY;
        }

        int chromaWidth = frameWidth / 2;
        int chromaHeight = validFrameHeight / 2;

        // Y-Plane
        if (mainTex == null || mainTex.width != frameWidth || mainTex.height != validFrameHeight)
        {
            mainTex = new Texture2D(frameWidth, validFrameHeight, TextureFormat.Alpha8, false);
            frameAspectRatio = (float)frameWidth / validFrameHeight;

            rawImage.texture = mainTex;
            rawImage.material.mainTexture = mainTex;
            SetAspectFitToImage();

            yBuffer = new byte[frameWidth * validFrameHeight];
        }
        UploadPlaneDataToTexture(
            unityPlayer,
            buffer,
            "copyYPlaneData",
            yBuffer,
            offsetY * frameWidth,
            frameWidth * validFrameHeight,
            mainTex);

        // U-Plane
        if (uTex == null || uTex.width != chromaWidth || uTex.height != chromaHeight)
        {
            uTex = new Texture2D(chromaWidth, chromaHeight, TextureFormat.Alpha8, false);
            rawImage.material.SetTexture("_UTex", uTex);
            uBuffer = new byte[chromaWidth * chromaHeight];
        }
        UploadPlaneDataToTexture(
            unityPlayer,
            buffer,
            "copyUPlaneData",
            uBuffer,
            (offsetY / 2) * chromaWidth,
            chromaWidth * chromaHeight,
            uTex);

        // V-Plane
        if (vTex == null || vTex.width != chromaWidth || vTex.height != chromaHeight)
        {
            vTex = new Texture2D(chromaWidth, chromaHeight, TextureFormat.Alpha8, false);
            rawImage.material.SetTexture("_VTex", vTex);
            vBuffer = new byte[chromaWidth * chromaHeight];
        }
        UploadPlaneDataToTexture(
            unityPlayer,
            buffer,
            "copyVPlaneData",
            vBuffer,
            (offsetY / 2) * chromaWidth,
            chromaWidth * chromaHeight,
            vTex);
    }

    /// <summary>
    /// Textureへ画像データをアップデートする
    /// </summary>
    /// <param name="unityPlugin">java層のUnityPluginクラスへのアクセスが可能なオブジェクト</param>
    /// <param name="yuvPlaneData">YUVデータが格納されたバッファ</param>
    /// <param name="methodName">java層のUnityPluginクラスの呼び出し先メソッド名</param>
    /// <param name="dstBuffer">Planeデータの書き込み先。このデータがTextureに転送される</param>
    /// <param name="offset">書き込み元バッファのオフセット値</param>
    /// <param name="size">書き込みサイズ</param>
    /// <param name="texture">書き込み先のTexture</param>
    private void UploadPlaneDataToTexture(
        AndroidJavaObject unityPlugin,
        AndroidJavaObject yuvPlaneData,
        string methodName,
        byte[] dstBuffer,
        int offset,
        int size,
        Texture2D texture)
    {
        var handle = default(GCHandle);
        try
        {
            handle = GCHandle.Alloc(dstBuffer, GCHandleType.Pinned);
            var ptr = handle.AddrOfPinnedObject();

            unityPlugin.Call(methodName, yuvPlaneData, ptr.ToInt64(), offset, size);
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

    /// <summary>
    /// アスペクト比を保ちながら、画像の中心を表示する
    /// </summary>
    private void SetAspectFitToImage()
    {
        Rect rect = new Rect(0, 0, 1, 1);
        if (isPodUI)
        {
            // 合成データ
            int cutWidth = compositeInfo.CutBottomRightX - compositeInfo.CutTopLeftX;
            int cutHeight = compositeInfo.CutBottomRightY - compositeInfo.CutTopLeftY;

            // Y方向はTexture転送時に表示エリア外は削除されて転送されている

            // 0.0-1.0で座標計算
            float uvX = (float)compositeInfo.CutTopLeftX / frameWidth;
            float uvWidth = (float)cutWidth / frameWidth;

            rect.x = uvX;
            rect.width = uvWidth;

            float frameAspectRatio = (float)cutWidth / cutHeight;

            if (frameAspectRatio > drawnAspectRatio)
            {
                rect.width = (drawnAspectRatio / frameAspectRatio) * uvWidth;
                rect.x += (uvWidth - rect.width) * 0.5f;
            }
            else
            {
                rect.height = frameAspectRatio / drawnAspectRatio;
                rect.y = (1 - rect.height) * 0.5f;
            }
        }
        else
        {

            if (frameAspectRatio > drawnAspectRatio)
            {
                rect.width = drawnAspectRatio / frameAspectRatio;
                rect.x = (1 - rect.width) * 0.5f;
            }
            else
            {
                rect.height = frameAspectRatio / drawnAspectRatio;
                rect.y = (1 - rect.height) * 0.5f;
            }
        }
        rawImage.uvRect = rect;
    }

    /// <summary>
    /// 表示領域サイズの更新
    /// </summary>
    /// <param name="newSize">サイズ</param>
    public void UpdateRectSize(Vector2 newSize)
    {
        RectTransform rt = rawImage.transform as RectTransform;
        drawnAspectRatio = rt.sizeDelta.x / rt.sizeDelta.y;
        SetAspectFitToImage();
    }
}


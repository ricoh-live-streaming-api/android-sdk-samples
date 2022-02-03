using UnityEngine;
using UnityEngine.UI;

public class RemoteView
{
    private readonly GameObject content;
    private readonly YUVMaterialTexture yuvMaterialTexture;
    private readonly string connectionId;
    private readonly string trackId;

    public RemoteView(
        string connectionId, string trackId, GameObject content, CompositeInfo compositeInfo, bool isPodUI)
    {
        this.content = content;
        this.connectionId = connectionId;
        this.trackId = trackId;
        var image = content.transform.Find("RemoteRawImage").GetComponent<RawImage>();
        Utils.SetRectSize(image, Utils.GetRectSize(content));
        yuvMaterialTexture = new YUVMaterialTexture(image, compositeInfo, isPodUI);
    }

    public GameObject Content { get { return content; } }

    public YUVMaterialTexture YUVMaterialTexture {  get { return yuvMaterialTexture; } }

    public string ConnectionId { get { return connectionId; } }

    public string TrackId { get { return trackId; } }

    public void UpdateRectSize(Vector2 size)
    {
        Utils.SetRectSize(content, size);
        var image = content.transform.Find("RemoteRawImage").GetComponent<RawImage>();
        Utils.SetRectSize(image, size);
        yuvMaterialTexture.UpdateRectSize(size);
    }

    public void UpdatePosition(Vector2 position)
    {
        Utils.SetLocalPosition(content, position);
    }
}

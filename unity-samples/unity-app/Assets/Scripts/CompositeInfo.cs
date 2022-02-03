/// <summary>
/// 合成フレーム情報
/// </summary>
public class CompositeInfo
{
    /// <summary>
    /// コンストラクタ
    /// </summary>
    /// <param name="frameWidth">合成したフレームの全体サイズのWidth</param>
    /// <param name="frameHeight">合成したフレームの全体サイズのHeight</param>
    /// <param name="cutTopLeftX">切り出し左上 X座標</param>
    /// <param name="cutTopLeftY">切り出し左上 Y座標</param>
    /// <param name="cutBottomRightX">切り出し右下 X座標</param>
    /// <param name="cutBottomRightY">切り出し右下 Y座標</param>
    public CompositeInfo(
        int frameWidth, int frameHeight,
        int cutTopLeftX, int cutTopLeftY,
        int cutBottomRightX, int cutBottomRightY)
    {
        FrameWidth = frameWidth;
        FrameHeight = frameHeight;
        CutTopLeftX = cutTopLeftX;
        CutTopLeftY = cutTopLeftY;
        CutBottomRightX = cutBottomRightX;
        CutBottomRightY = cutBottomRightY;
    }

    /// <summary>
    /// 合成したフレームの全体サイズのWidth
    /// </summary>
    public int FrameWidth { get; }

    /// <summary>
    /// 合成したフレームの全体サイズのHeight
    /// </summary>
    public int FrameHeight { get; }

    /// <summary>
    /// 切り出し左上 X座標
    /// </summary>
    public int CutTopLeftX { get; }

    /// <summary>
    /// 切り出し左上 Y座標
    /// </summary>
    public int CutTopLeftY { get; }

    /// <summary>
    /// 切り出し右下 X座標
    /// </summary>
    public int CutBottomRightX { get; }

    /// <summary>
    /// 切り出し右下 Y座標
    /// </summary>
    public int CutBottomRightY { get; }

}

using System.Collections.Generic;
using UnityEngine;
using System.Linq;
using System;

public class VideoRenderManager
{
    private readonly IDictionary<string, VideoTrackLocal> remoteTrackMap = new Dictionary<string, VideoTrackLocal>();
    private int remoteVideoShowIndex = 0;
    private static readonly object _lock = new object();
    private readonly LiveStreaming.FrameUpdateListener frameUpdateListener;
    private Logger logger;

    public enum VideoFormat
    {
        Normal,
        Equi,
        DualFisheye
    }

    public VideoRenderManager(LiveStreaming.FrameUpdateListener listener)
    {
        logger = LoggerFactory.GetLogger(this.GetType().Name);
        frameUpdateListener = listener;
    }

    /// <summary>
    /// Add remote track.
    /// </summary>
    /// <param name="connectionId">Connection ID</param>
    /// <param name="track">Video track</param>
    /// <param name="format">Video format</param>
    public void AddRemoteTrack(string connectionId, AndroidJavaObject track, VideoFormat format)
    {
        logger.Info(string.Format("AddRemoteTrack(connectionId={0} format={1})", connectionId, format));

        lock (_lock)
        {
            if (remoteTrackMap.ContainsKey(connectionId))
            {
                // Delete old track.
                RemoveRemoteTrack(connectionId);
            }

            VideoTrackLocal videoTrackLocal = new VideoTrackLocal(this, track, format);
            remoteTrackMap.Add(connectionId, videoTrackLocal);
            if (!IsShowing())
            {
                videoTrackLocal.Show();
            }
        }
    }

    /// <summary>
    /// Remove remote track.
    /// </summary>
    /// <param name="connectionId">Connection ID</param>
    public void RemoveRemoteTrack(string connectionId)
    {
        logger.Info(string.Format("RemoveRemoteTrack(connectionId={0})", connectionId));
        lock (_lock)
        {
            bool isShowing = IsShowing(connectionId);
            remoteTrackMap.Remove(connectionId);
            if (isShowing && remoteTrackMap.Count >= 1)
            {
                remoteTrackMap.First().Value.Show();
                remoteVideoShowIndex = 0;
            }
        }
    }

    /// <summary>
    /// Get number of retmote tracks.
    /// </summary>
    /// <returns>Number of remote tracks.</returns>
    public int GetNumOfRemoteTrack()
    {
        lock (_lock)
        {
            return remoteTrackMap.Count;
        }
    }

    public void Clear()
    {
        lock (_lock)
        {
            remoteTrackMap.Clear();
        }
    }

    /// <summary>
    /// Switch display participant by toggle.
    /// </summary>
    public void ToggleDisplay()
    {
        lock (_lock)
        {
            Hide();
            remoteVideoShowIndex++;

            if (remoteTrackMap.Count <= remoteVideoShowIndex)
            {
                KeyValuePair<string, VideoTrackLocal> next = remoteTrackMap.First();
                next.Value.Show();
                remoteVideoShowIndex = 0;
            }
            else
            {
                int i = 0;
                foreach (VideoTrackLocal track in remoteTrackMap.Values)
                {
                    if (i == remoteVideoShowIndex)
                    {
                        track.Show();
                        break;
                    }

                    i++;
                }

            }
        }
    }

    /// <summary>
    /// 現在表示中のTrackのビデオフォーマットを取得する
    /// </summary>
    /// <returns>ビデオフォーマット</returns>
    /// <exception cref="Exception">表示中のTrackがみつからない場合にthrowする</exception>
    public VideoFormat GetCurrentShowingTrackVideoFormat()
    {
        lock (_lock)
        {
            var track = remoteTrackMap
                .Values
                .Cast<VideoTrackLocal>()
                .FirstOrDefault(t => t.IsShowing);
            if (track != null)
            {
                return track.Format;
            }

            throw new Exception("Not found showing track.");
        }
    }

    private void Hide()
    {
        foreach (VideoTrackLocal track in remoteTrackMap.Values)
        {
            if (track.IsShowing)
            {
                track.Hide();
                break;
            }
        }
    }

    private bool IsShowing(string id)
    {
        if (!remoteTrackMap.ContainsKey(id))
        {
            return false;
        }

        return remoteTrackMap[id].IsShowing;
    }

    private bool IsShowing() {
        foreach (VideoTrackLocal track in remoteTrackMap.Values)
        {
            if (track.IsShowing)
            {
                return true;
            }
        }

        return false;
    }


    private class VideoTrackLocal {
        private readonly VideoRenderManager _manager;
        private readonly AndroidJavaObject _track;
        private readonly string _id;
        public bool IsShowing { get; private set; } = false;
        public VideoFormat Format { get; set; }

        public VideoTrackLocal(VideoRenderManager manager, AndroidJavaObject track, VideoFormat format)
        {
            _manager = manager;
            _track = track;
            _id = track.Call<string>("id");
            Format = format;
        }

        public void Show()
        {
            _manager.logger.Debug(string.Format("Show({0})", _id));
            _track.Call("addSink", _manager.frameUpdateListener);
            IsShowing = true;
        }

        public void Hide()
        {
            _manager.logger.Debug(string.Format("Hide({0})", _id));
            _track.Call("removeSink", _manager.frameUpdateListener);
            IsShowing = false;
        }
    }
}
/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.wearable_glass

import org.slf4j.LoggerFactory
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class VideoRenderManager(private val view: SurfaceViewRenderer) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(VideoRenderManager::class.java)
    }

    private enum class LayoutMode {
        /**
         * Display the another participant with precedence.
         * When there are no participants, display the self.
         */
        AUTO,

        /**
         * Display the self.
         */
        SELF
    }

    private var mLayoutMode: LayoutMode = LayoutMode.AUTO
    private val mRemoteTrackMap: MutableMap<String, VideoTrackLocal> = mutableMapOf()
    private var mLocalVideo: VideoTrackLocal? = null
    private var mRemoteVideoShowIndex = 0

    /**
     * Add local track.
     */
    @Synchronized
    fun addLocalTrack(videoTrack: VideoTrack) {
        LOGGER.debug("addLocalTrack(${videoTrack.id()})")

        mLocalVideo = VideoTrackLocal(videoTrack)

        if (!isShowing() || mLayoutMode == LayoutMode.SELF) {
            // hide remote video
            hide()

            // show local video.
            showLocalVideo()
        }
    }

    /**
     * Add remote track.
     */
    @Synchronized
    fun addRemoteTrack(connectionId: String, videoTrack: VideoTrack) {
        LOGGER.debug("addRemoteTrack(${connectionId})")

        mRemoteTrackMap[connectionId] = VideoTrackLocal(videoTrack)

        if (!isShowing() ||
                (isLocalVideoShowing() && mLayoutMode == LayoutMode.AUTO)
        ) {
            // If the layout mode is AUTO and the self is already displayed,
            // switch the display to the another participants.

            hideLocalVideo()
            show(connectionId)
        }

    }

    /**
     * Remove remote track.
     */
    @Synchronized
    fun removeRemoteTrack(connectionId: String) {
        LOGGER.debug("removeRemoteTrack(${connectionId})")

        val isShowing = isShowing(connectionId)
        mRemoteTrackMap.remove(connectionId)

        if (isShowing) {
            hide()
            // If the currently displayed participant is deleted, display the next.
            if (hasRemoteTrack()) {
                // show remote video
                showFirstRemoteVideo()
                mRemoteVideoShowIndex = 0
            } else {
                // show local video
                showLocalVideo()
            }
        }
    }

    @Synchronized
    fun clear() {
        mLocalVideo = null
        mRemoteTrackMap.clear()
    }

    /**
     * Switch display participant by toggle.
     * A -> B -> SELF -> A -> ...
     */
    @Synchronized
    fun toggleDisplay() {
        LOGGER.debug("showNextTrack()")

        when (mLayoutMode) {
            LayoutMode.AUTO -> {
                mRemoteVideoShowIndex++
                if (mRemoteTrackMap.size <= mRemoteVideoShowIndex) {
                    hide()
                    showLocalVideo()
                    mRemoteVideoShowIndex = 0
                    mLayoutMode = LayoutMode.SELF
                } else {
                    var index = 0
                    for (track in mRemoteTrackMap) {
                        if (mRemoteVideoShowIndex == index) {
                            hide()
                            mLayoutMode = LayoutMode.AUTO
                            track.value.show()
                            break
                        }
                        index++
                    }
                }
            }
            LayoutMode.SELF -> {
                if (hasRemoteTrack()) {
                    hideLocalVideo()
                    showFirstRemoteVideo()
                    mLayoutMode = LayoutMode.AUTO
                }
            }
        }
    }

    private fun hasRemoteTrack(): Boolean {
        return mRemoteTrackMap.isNotEmpty()
    }

    private fun isLocalVideoShowing(): Boolean {
        return mLocalVideo?.isShowing() ?: false
    }

    private fun show(id: String) {
        if (mLocalVideo?.id() == id) {
            mLocalVideo?.show()
            return
        }

        val track = mRemoteTrackMap[id]
        track?.show()
    }

    private fun showLocalVideo() {
        mLocalVideo?.show()
    }

    private fun showFirstRemoteVideo() {
        mRemoteVideoShowIndex = 0
        val track = mRemoteTrackMap.iterator().next().value
        track.show()
    }


    private fun hideLocalVideo() {
        if (mLocalVideo?.isShowing() == true) {
            mLocalVideo?.hide()
        }
    }

    private fun hide() {
        for (track in mRemoteTrackMap.values) {
            if (track.isShowing()) {
                track.hide()
            }
        }
    }

    private fun isShowing(id: String): Boolean {
        if (mLocalVideo?.id() == id) {
            return isLocalVideoShowing()
        }

        val track = mRemoteTrackMap[id]
        return track?.isShowing() ?: false
    }

    private fun isShowing(): Boolean {
        if (mLocalVideo?.isShowing() == true) {
            return true
        }

        for (track in mRemoteTrackMap.values) {
            if (track.isShowing()) {
                return true
            }
        }

        return false
    }

    inner class VideoTrackLocal(private val videoTrack: VideoTrack) {
        private var isShowing: Boolean = false
        private val videoTrackId: String = videoTrack.id()

        fun id(): String {
            return videoTrackId
        }

        fun isShowing(): Boolean {
            return isShowing
        }

        fun show() {
            LOGGER.debug("show(${videoTrackId})")
            videoTrack.addSink(view)
            isShowing = true
        }

        fun hide() {
            LOGGER.debug("hide(${videoTrackId})")
            videoTrack.removeSink(view)
            view.clearImage()
            isShowing = false
        }
    }
}
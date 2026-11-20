package com.tenban.screenshare

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import org.webrtc.*
import java.net.URI

class WebRTCClient(
    private val context: Context,
    private val serverUrl: String,
    private val roomId: String
) {
    private lateinit var eglBase: EglBase
    private lateinit var factory: PeerConnectionFactory
    private lateinit var peerConnection: PeerConnection
    private lateinit var videoSource: VideoSource
    private lateinit var videoTrack: VideoTrack
    private lateinit var capturer: ScreenCapturerAndroid
    private lateinit var socket: Socket

    fun start(projectionData: Intent) {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        eglBase = EglBase.create()
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()

        capturer = ScreenCapturerAndroid(projectionData, object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection stopped")
                stop()
            }
        })
        val surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        videoSource = factory.createVideoSource(false)
        capturer.initialize(surfaceHelper, context, videoSource.capturerObserver)
        capturer.startCapture(1280, 720, 20)
        videoTrack = factory.createVideoTrack("VIDEO", videoSource)

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                socket.emit("ice-candidate", JSONObject().apply {
                    put("room", roomId)
                    put("candidate", candidate.sdp)
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                })
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE state: $state")
            }
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {}
            override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(r: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(s: MediaStream?) {}
            override fun onRemoveStream(s: MediaStream?) {}
            override fun onDataChannel(c: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(r: RtpReceiver?, s: Array<out MediaStream>?) {}
        })!!
        peerConnection.addTrack(videoTrack, listOf("stream0"))

        connectSocket()
    }

    private fun connectSocket() {
        val opts = IO.Options().apply {
            transports = arrayOf(io.socket.engineio.client.transports.WebSocket.NAME)
            reconnection = true
        }
        socket = IO.socket(URI.create(serverUrl), opts)
        socket.on(Socket.EVENT_CONNECT) {
            Log.d(TAG, "Socket connected to $serverUrl")
            socket.emit("join-room", JSONObject().put("room", roomId))
            createOffer()
        }
        socket.on(Socket.EVENT_CONNECT_ERROR) { args ->
            Log.e(TAG, "Socket connect error: ${args.firstOrNull()}")
        }
        socket.on("answer") { args ->
            val json = args[0] as JSONObject
            val sdp = json.getString("sdp")
            peerConnection.setRemoteDescription(
                SimpleSdpObserver(),
                SessionDescription(SessionDescription.Type.ANSWER, sdp)
            )
            Log.d(TAG, "Answer received & set")
        }
        socket.on("ice-candidate") { args ->
            val json = args[0] as JSONObject
            peerConnection.addIceCandidate(
                IceCandidate(
                    json.getString("sdpMid"),
                    json.getInt("sdpMLineIndex"),
                    json.getString("candidate")
                )
            )
        }
        socket.connect()
    }

    private fun createOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        peerConnection.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection.setLocalDescription(SimpleSdpObserver(), sdp)
                socket.emit("offer", JSONObject().apply {
                    put("room", roomId)
                    put("sdp", sdp.description)
                    put("type", sdp.type.canonicalForm())
                })
                Log.d(TAG, "Offer sent for room $roomId")
            }
        }, constraints)
    }

    fun stop() {
        try { capturer.stopCapture() } catch (_: Exception) {}
        capturer.dispose()
        videoTrack.dispose()
        videoSource.dispose()
        peerConnection.close()
        factory.dispose()
        eglBase.release()
        socket.disconnect()
        socket.close()
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) { Log.e(TAG, "SDP create fail: $error") }
        override fun onSetFailure(error: String?) { Log.e(TAG, "SDP set fail: $error") }
    }

    companion object { private const val TAG = "WebRTC" }
}
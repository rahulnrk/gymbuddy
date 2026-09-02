package com.gymbuddy.app.voice

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class OkHttpTtsTransport(
    private val client: OkHttpClient = OkHttpClient(),
) : TtsTransport {
    private var socket: WebSocket? = null

    override fun open(url: String, headers: Map<String, String>, listener: TtsTransport.Listener) {
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        socket = client.newWebSocket(
            builder.build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    listener.onOpen()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    listener.onMessage(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    listener.onFailure(t, response?.code)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    listener.onClosed()
                }
            },
        )
    }

    override fun send(text: String) {
        socket?.send(text)
    }

    override fun close() {
        socket?.close(1000, "done")
        socket = null
    }
}

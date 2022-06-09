package com.example.wificlient.net

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException

class WifiClient {

    private var clientThread: ClientThread? = null
    private var commThread: CommThread? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var socketListener: SocketListener? = null

    fun setOnSocketListener(listener: SocketListener?) {
        socketListener = listener
    }

    fun onConnect() {
        socketListener?.onConnect()
    }

    fun onDisconnect() {
        socketListener?.onDisconnect()
    }

    fun onLogPrint(message: String?) {
        socketListener?.onLogPrint(message)
    }

    fun onError(e: Exception) {
        socketListener?.onError(e)
    }

    fun onReceive(msg: String) {
        socketListener?.onReceive(msg)
    }

    fun onSend(msg: String) {
        socketListener?.onSend(msg)
    }

    fun connectToServer(ipAddress: String, port: Int?) {
        if (port == null) return

        disconnectFromServer()
        if (clientThread != null) {
            clientThread?.stopThread()
        }

        onLogPrint("Connect to server.")
        clientThread = ClientThread(ipAddress, port)
        clientThread?.start()
    }

    fun disconnectFromServer() {
        if (clientThread == null) return

        try {
            clientThread?.let {
                onDisconnect();

                it.stopThread()
                it.join(1000)
                it.interrupt()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    inner class ClientThread(private val ipAddress: String, private val port: Int) : Thread() {
        private var socket: Socket? = null

        override fun run() {
            try {
                val socketAddress: InetSocketAddress = try {
                    InetSocketAddress(InetAddress.getByName(ipAddress), port)
                } catch (e: UnknownHostException) {
                    e.printStackTrace()
                    return
                }

                onLogPrint("Try to connect to server..")
                socket?.connect(socketAddress, 5000) // 소켓 연결

            } catch (e: Exception) {
                e.printStackTrace()
                onError(e)
                return
            }

            if (socket != null) {
                onConnect()

                commThread = CommThread(socket)
                commThread?.start()
            }
        }

        fun stopThread() {
            try {
                socket?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        init {
            try {
                // 소켓 생성
                socket = Socket()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    internal inner class CommThread(private val socket: Socket?) : Thread() {

        override fun run() {
            try {
                outputStream = socket?.outputStream
                inputStream = socket?.inputStream
            } catch (e: Exception) {
                e.printStackTrace()
            }

            var len: Int
            val buffer = ByteArray(1024)
            val byteArrayOutputStream = ByteArrayOutputStream()

            while (true) {
                try {
                    len = socket?.inputStream?.read(buffer)!!
                    val data = buffer.copyOf(len)
                    byteArrayOutputStream.write(data)

                    socket.inputStream?.available()?.let { available ->

                        if (available == 0) {
                            val dataByteArray = byteArrayOutputStream.toByteArray()
                            val dataString = String(dataByteArray)
                            onReceive(dataString)

                            byteArrayOutputStream.reset()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    stopThread()
                    disconnectFromServer()
                    break
                }
            }
        }

        fun stopThread() {
            try {
                inputStream?.close()
                outputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendData(msg: String) {
        if (outputStream == null) return

        object : Thread() {
            override fun run() {
                try {
                    outputStream?.let {
                        onSend(msg)

                        it.write(msg.toByteArray())
                        it.flush()
                    }
                } catch (e: Exception) {
                    onError(e)
                    e.printStackTrace()
                    disconnectFromServer()
                }
            }
        }.start()
    }
}
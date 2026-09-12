package de.gabriel.nearping.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import de.gabriel.nearping.protocol.CURRENT_PROTOCOL_VERSION
import de.gabriel.nearping.protocol.MAX_WIRE_BYTES
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.crypto.spec.SecretKeySpec

/**
 * Discovers NearPing instances through DNS-SD/mDNS and connects them directly over TCP.
 * Payloads are encrypted with a fresh ephemeral ECDH key for each socket. The key exchange
 * protects against passive WLAN observers but deliberately does not claim verified identity.
 */
class LanPeerTransport(context: Context) : PeerTransport {
    private data class PendingResolution(
        val sessionId: String,
        val serviceInfo: NsdServiceInfo,
    )

    private data class LanConnection(
        val endpointId: String,
        val remoteSessionId: String,
        val outgoing: Boolean,
        val socket: Socket,
        val input: DataInputStream,
        val output: DataOutputStream,
        val sendKey: SecretKeySpec,
        val receiveKey: SecretKeySpec,
        val writeLock: Any = Any(),
        var sendCounter: Long = 0L,
        var receiveCounter: Long = 0L,
    )

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val connectionLock = Any()
    private val connectionsByEndpoint = linkedMapOf<String, LanConnection>()
    private val endpointBySession = mutableMapOf<String, String>()
    private val resolveQueue = ArrayDeque<PendingResolution>()
    private val queuedSessions = mutableSetOf<String>()
    private val scheduledSessions = mutableSetOf<String>()
    private val connectingSessions = mutableSetOf<String>()
    private val lostSessions = mutableSetOf<String>()

    private var listener: PeerTransport.Listener? = null
    private var localSessionId: String = ""
    private var running = false
    private var serverSocket: ServerSocket? = null
    private var ioExecutor: ExecutorService? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var serviceRegistered = false
    private var discoveryStarted = false
    private var resolvingSessionId: String? = null

    override fun start(localSessionId: String, listener: PeerTransport.Listener) {
        if (running) {
            listener.onTransportError("LAN-Suche läuft bereits.")
            return
        }
        this.localSessionId = localSessionId
        this.listener = listener
        running = true
        ioExecutor = Executors.newFixedThreadPool(IO_THREAD_COUNT)

        val server = runCatching {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(0))
            }
        }.getOrElse { error ->
            listener.onTransportError("LAN-Empfang konnte nicht gestartet werden: ${error.safeMessage()}.")
            stop()
            return
        }
        serverSocket = server

        runCatching {
            wifiManager.createMulticastLock(MULTICAST_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onSuccess { multicastLock = it }

        listener.onTransportStatus("starte lokalen WLAN-Dienst …")
        startAcceptLoop(server)
        registerService(server.localPort)
        startDiscovery()
    }

    override fun send(endpointId: String, bytes: ByteArray) {
        if (!running || bytes.size !in 1..MAX_WIRE_BYTES) return
        val connection = synchronized(connectionLock) { connectionsByEndpoint[endpointId] } ?: return
        ioExecutor?.execute {
            runCatching { writeEncrypted(connection, bytes) }
                .onFailure { closeConnection(connection, notifyListener = true) }
        }
    }

    override fun stop() {
        val wasRunning = running
        running = false
        mainHandler.removeCallbacksAndMessages(null)

        if (discoveryStarted) {
            discoveryListener?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
        }
        if (serviceRegistered) {
            registrationListener?.let { runCatching { nsdManager.unregisterService(it) } }
        }
        discoveryStarted = false
        serviceRegistered = false
        discoveryListener = null
        registrationListener = null

        runCatching { serverSocket?.close() }
        serverSocket = null
        val connections = synchronized(connectionLock) {
            val snapshot = connectionsByEndpoint.values.toList()
            connectionsByEndpoint.clear()
            endpointBySession.clear()
            snapshot
        }
        connections.forEach { runCatching { it.socket.close() } }
        ioExecutor?.shutdownNow()
        ioExecutor = null
        multicastLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        multicastLock = null

        resolveQueue.clear()
        queuedSessions.clear()
        scheduledSessions.clear()
        connectingSessions.clear()
        lostSessions.clear()
        resolvingSessionId = null
        localSessionId = ""
        if (wasRunning) listener = null
    }

    private fun startAcceptLoop(server: ServerSocket) {
        ioExecutor?.execute {
            while (running) {
                val socket = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                val connectionCount = synchronized(connectionLock) { connectionsByEndpoint.size }
                if (!running || connectionCount >= MAX_LAN_CONNECTIONS) {
                    runCatching { socket.close() }
                    continue
                }
                ioExecutor?.execute { establishConnection(socket, outgoing = false, expectedSessionId = null) }
            }
        }
    }

    private fun registerService(port: Int) {
        val callback = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                mainHandler.post {
                    serviceRegistered = true
                    if (running) {
                        listener?.onTransportStatus("im lokalen WLAN sichtbar; starte Suche …")
                    } else {
                        runCatching { nsdManager.unregisterService(this) }
                        serviceRegistered = false
                    }
                }
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                postToMain {
                    listener?.onTransportError("LAN-Ankündigung fehlgeschlagen (NSD $errorCode).")
                }
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = callback
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "$SERVICE_NAME_PREFIX$localSessionId"
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute(SESSION_ATTRIBUTE, localSessionId)
        }
        runCatching {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, callback)
        }.onFailure { error ->
            listener?.onTransportError("LAN-Ankündigung fehlgeschlagen: ${error.safeMessage()}.")
        }
    }

    private fun startDiscovery() {
        val callback = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                mainHandler.post {
                    discoveryStarted = true
                    if (running) {
                        listener?.onTransportStatus("lokale WLAN-Suche aktiv …")
                    } else {
                        runCatching { nsdManager.stopServiceDiscovery(this) }
                        discoveryStarted = false
                    }
                }
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val remoteSessionId = extractSessionId(serviceInfo.serviceName) ?: return
                if (remoteSessionId == localSessionId) return
                postToMain { scheduleResolution(remoteSessionId, serviceInfo) }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val remoteSessionId = extractSessionId(serviceInfo.serviceName) ?: return
                postToMain {
                    lostSessions += remoteSessionId
                    if (remoteSessionId in scheduledSessions) {
                        mainHandler.removeCallbacksAndMessages(remoteSessionId)
                        scheduledSessions -= remoteSessionId
                    }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                postToMain {
                    listener?.onTransportError("LAN-Suche fehlgeschlagen (NSD $errorCode).")
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        discoveryListener = callback
        runCatching {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, callback)
        }.onFailure { error ->
            listener?.onTransportError("LAN-Suche fehlgeschlagen: ${error.safeMessage()}.")
        }
    }

    private fun scheduleResolution(remoteSessionId: String, serviceInfo: NsdServiceInfo) {
        if (!running || hasConnection(remoteSessionId) || remoteSessionId in scheduledSessions ||
            remoteSessionId in queuedSessions || remoteSessionId in connectingSessions
        ) return

        lostSessions -= remoteSessionId
        scheduledSessions += remoteSessionId
        val delay = if (localSessionId < remoteSessionId) 0L else FALLBACK_CONNECT_DELAY_MS
        mainHandler.postAtTime(
            {
                scheduledSessions -= remoteSessionId
                if (running && remoteSessionId !in lostSessions && !hasConnection(remoteSessionId)) {
                    queuedSessions += remoteSessionId
                    resolveQueue += PendingResolution(remoteSessionId, serviceInfo)
                    drainResolveQueue()
                }
            },
            remoteSessionId,
            android.os.SystemClock.uptimeMillis() + delay,
        )
    }

    @Suppress("DEPRECATION")
    private fun drainResolveQueue() {
        if (!running || resolvingSessionId != null) return
        while (resolveQueue.isNotEmpty()) {
            val pending = resolveQueue.removeFirst()
            queuedSessions -= pending.sessionId
            if (hasConnection(pending.sessionId) || pending.sessionId in lostSessions) continue

            resolvingSessionId = pending.sessionId
            val callback = object : NsdManager.ResolveListener {
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    postToMain {
                        resolvingSessionId = null
                        connectResolvedService(pending.sessionId, serviceInfo)
                        drainResolveQueue()
                    }
                }

                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    postToMain {
                        resolvingSessionId = null
                        listener?.onTransportStatus("WLAN-Dienst konnte nicht aufgelöst werden (NSD $errorCode).")
                        drainResolveQueue()
                    }
                }
            }
            runCatching { nsdManager.resolveService(pending.serviceInfo, callback) }
                .onFailure { error ->
                    resolvingSessionId = null
                    listener?.onTransportStatus("WLAN-Auflösung fehlgeschlagen: ${error.safeMessage()}.")
                    continue
                }
            if (resolvingSessionId != null) return
        }
    }

    @Suppress("DEPRECATION")
    private fun connectResolvedService(expectedSessionId: String, serviceInfo: NsdServiceInfo) {
        if (!running || expectedSessionId in lostSessions || hasConnection(expectedSessionId) ||
            expectedSessionId in connectingSessions
        ) return
        val host = serviceInfo.host ?: return
        val port = serviceInfo.port
        if (port !in 1..65535) return

        connectingSessions += expectedSessionId
        ioExecutor?.execute {
            val socket = Socket()
            val result = runCatching {
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                establishConnection(socket, outgoing = true, expectedSessionId = expectedSessionId)
            }
            if (result.isFailure) runCatching { socket.close() }
            postToMain {
                connectingSessions -= expectedSessionId
                if (result.isFailure && !hasConnection(expectedSessionId)) {
                    listener?.onTransportStatus("WLAN-Verbindung wird bei erneuter Erkennung wiederholt …")
                }
            }
        }
    }

    private fun establishConnection(
        socket: Socket,
        outgoing: Boolean,
        expectedSessionId: String?,
    ) {
        socket.tcpNoDelay = true
        socket.keepAlive = true
        socket.soTimeout = HANDSHAKE_TIMEOUT_MS
        val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
        val keyPair = LanCrypto.generateKeyPair()

        output.writeInt(HANDSHAKE_MAGIC)
        output.writeInt(LAN_TRANSPORT_VERSION)
        output.writeInt(CURRENT_PROTOCOL_VERSION)
        output.writeUTF(localSessionId)
        output.writeInt(keyPair.public.encoded.size)
        output.write(keyPair.public.encoded)
        output.flush()

        require(input.readInt() == HANDSHAKE_MAGIC) { "Unknown LAN peer" }
        require(input.readInt() == LAN_TRANSPORT_VERSION) { "Incompatible LAN transport" }
        require(input.readInt() == CURRENT_PROTOCOL_VERSION) { "Incompatible wire protocol" }
        val remoteSessionId = input.readUTF()
        require(SESSION_ID_REGEX.matches(remoteSessionId) && remoteSessionId != localSessionId) {
            "Invalid remote session"
        }
        require(expectedSessionId == null || expectedSessionId == remoteSessionId) {
            "Resolved session does not match handshake"
        }
        val publicKeyLength = input.readInt()
        require(publicKeyLength in MIN_PUBLIC_KEY_BYTES..MAX_PUBLIC_KEY_BYTES) {
            "Invalid public key"
        }
        val encodedPublicKey = ByteArray(publicKeyLength)
        input.readFully(encodedPublicKey)
        val remotePublicKey = LanCrypto.decodePublicKey(encodedPublicKey)
        val keys = LanCrypto.deriveSessionKeys(
            localPrivateKey = keyPair.private,
            remotePublicKey = remotePublicKey,
            localSessionId = localSessionId,
            remoteSessionId = remoteSessionId,
        )
        socket.soTimeout = 0

        val connection = LanConnection(
            endpointId = UUID.randomUUID().toString(),
            remoteSessionId = remoteSessionId,
            outgoing = outgoing,
            socket = socket,
            input = input,
            output = output,
            sendKey = keys.sendKey,
            receiveKey = keys.receiveKey,
        )
        if (!registerConnection(connection)) {
            socket.close()
            return
        }
        readLoop(connection)
    }

    private fun registerConnection(candidate: LanConnection): Boolean {
        var replaced: LanConnection? = null
        synchronized(connectionLock) {
            if (!running || connectionsByEndpoint.size >= MAX_LAN_CONNECTIONS) return false
            val existing = endpointBySession[candidate.remoteSessionId]
                ?.let(connectionsByEndpoint::get)
            if (existing != null) {
                val preferredOutgoing = localSessionId < candidate.remoteSessionId
                val candidatePreferred = candidate.outgoing == preferredOutgoing
                val existingPreferred = existing.outgoing == preferredOutgoing
                if (!candidatePreferred || existingPreferred) return false
                replaced = existing
            }
            connectionsByEndpoint[candidate.endpointId] = candidate
            endpointBySession[candidate.remoteSessionId] = candidate.endpointId
            replaced?.let { connectionsByEndpoint.remove(it.endpointId) }
        }

        emitPeerConnected(candidate)
        replaced?.let { oldConnection ->
            runCatching { oldConnection.socket.close() }
            emitPeerDisconnected(oldConnection.endpointId)
        }
        return true
    }

    private fun readLoop(connection: LanConnection) {
        try {
            while (running && !connection.socket.isClosed) {
                val encryptedLength = connection.input.readInt()
                require(encryptedLength in GCM_TAG_BYTES..MAX_ENCRYPTED_BYTES) {
                    "Invalid encrypted frame length"
                }
                val counter = connection.input.readLong()
                require(counter == connection.receiveCounter) { "Unexpected LAN frame counter" }
                val encrypted = ByteArray(encryptedLength)
                connection.input.readFully(encrypted)
                val plaintext = decrypt(connection, counter, encrypted)
                require(plaintext.size in 1..MAX_WIRE_BYTES) { "Invalid LAN payload" }
                connection.receiveCounter += 1
                emitBytes(connection.endpointId, plaintext)
            }
        } catch (_: EOFException) {
            // Normal peer shutdown.
        } catch (_: Exception) {
            // The connection is removed below; malformed frames are never forwarded.
        } finally {
            closeConnection(connection, notifyListener = true)
        }
    }

    private fun writeEncrypted(connection: LanConnection, bytes: ByteArray) {
        synchronized(connection.writeLock) {
            val counter = connection.sendCounter
            val encrypted = LanCrypto.encrypt(
                key = connection.sendKey,
                senderSessionId = localSessionId,
                targetSessionId = connection.remoteSessionId,
                counter = counter,
                plaintext = bytes,
            )
            connection.output.writeInt(encrypted.size)
            connection.output.writeLong(counter)
            connection.output.write(encrypted)
            connection.output.flush()
            connection.sendCounter += 1
        }
    }

    private fun decrypt(connection: LanConnection, counter: Long, encrypted: ByteArray): ByteArray {
        return LanCrypto.decrypt(
            key = connection.receiveKey,
            senderSessionId = connection.remoteSessionId,
            targetSessionId = localSessionId,
            counter = counter,
            ciphertext = encrypted,
        )
    }

    private fun closeConnection(connection: LanConnection, notifyListener: Boolean) {
        val removed = synchronized(connectionLock) {
            val current = connectionsByEndpoint[connection.endpointId]
            if (current !== connection) return@synchronized false
            connectionsByEndpoint.remove(connection.endpointId)
            if (endpointBySession[connection.remoteSessionId] == connection.endpointId) {
                endpointBySession.remove(connection.remoteSessionId)
            }
            true
        }
        runCatching { connection.socket.close() }
        if (removed && notifyListener) emitPeerDisconnected(connection.endpointId)
    }

    private fun hasConnection(sessionId: String): Boolean = synchronized(connectionLock) {
        endpointBySession[sessionId]?.let(connectionsByEndpoint::containsKey) == true
    }

    private fun emitPeerConnected(connection: LanConnection) = postToMain {
        listener?.onPeerConnected(
            ConnectedPeer(
                endpointId = connection.endpointId,
                advertisedSessionId = connection.remoteSessionId,
                transportKind = TransportKind.LAN,
            ),
        )
    }

    private fun emitPeerDisconnected(endpointId: String) = postToMain {
        listener?.onPeerDisconnected(endpointId)
    }

    private fun emitBytes(endpointId: String, bytes: ByteArray) = postToMain {
        listener?.onBytesReceived(endpointId, bytes)
    }

    private fun postToMain(block: () -> Unit) {
        mainHandler.post {
            if (running) block()
        }
    }

    private fun extractSessionId(serviceName: String): String? {
        if (!serviceName.startsWith(SERVICE_NAME_PREFIX)) return null
        return SESSION_ID_REGEX.find(serviceName.removePrefix(SERVICE_NAME_PREFIX))
            ?.takeIf { it.range.first == 0 }
            ?.value
    }

    private fun Throwable.safeMessage(): String =
        message?.take(120)?.takeIf(String::isNotBlank) ?: javaClass.simpleName

    companion object {
        private const val SERVICE_TYPE = "_nearping._tcp."
        private const val SERVICE_NAME_PREFIX = "NearPing-v1-"
        private const val SESSION_ATTRIBUTE = "session"
        private const val MULTICAST_LOCK_TAG = "NearPing:LanDiscovery"
        private const val HANDSHAKE_MAGIC = 0x4E504C31
        private const val HANDSHAKE_TIMEOUT_MS = 6_000
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val FALLBACK_CONNECT_DELAY_MS = 1_800L
        private const val MAX_LAN_CONNECTIONS = 16
        private const val IO_THREAD_COUNT = MAX_LAN_CONNECTIONS + 4
        private const val MIN_PUBLIC_KEY_BYTES = 64
        private const val MAX_PUBLIC_KEY_BYTES = 512
        private const val GCM_TAG_BYTES = LAN_GCM_TAG_BYTES
        private const val MAX_ENCRYPTED_BYTES = MAX_WIRE_BYTES + LAN_GCM_TAG_BYTES
        private val SESSION_ID_REGEX = Regex("^[0-9a-f]{16}")
    }
}

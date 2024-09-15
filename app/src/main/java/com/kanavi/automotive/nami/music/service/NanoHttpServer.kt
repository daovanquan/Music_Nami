package com.kanavi.automotive.nami.music.service

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.kanavi.automotive.nami.music.common.extension.getMimeType
import com.kanavi.automotive.nami.music.service.MusicService.Companion.SAMBA_SERVER_SHARE_NAME
import com.kanavi.automotive.nami.music.service.MusicService.Companion.fileAttributes
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.net.ServerSocket
import java.util.EnumSet

class NanoHttpServer(private val sambaServerIP: String, port: Int) : NanoHTTPD(port) {
    private val serverScope = CoroutineScope(SupervisorJob() + Main)

    private val sambaClient = SMBClient()
    private var sambaConnection: Connection? = null
    private var sambaSession: Session? = null
    private var diskShare: DiskShare? = null

    init {
        connectToSambaServer(sambaServerIP)
    }

    override fun serve(session: IHTTPSession?): Response {
        Timber.i("Request received: ${session?.uri}")
        if (diskShare == null || !diskShare!!.isConnected) {
            Timber.d("Share is not connected, attempting to reconnect...")
            connectToSambaServer(sambaServerIP)
        }

        if (diskShare == null) {
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Error: Failed to connect to Samba share."
            )
        }

        val uri = session?.uri ?: return newFixedLengthResponse(
            Response.Status.NOT_FOUND,
            MIME_PLAINTEXT,
            "Not Found"
        )
        val filePath = uri.removePrefix("/")
        Timber.i("Requested file: $filePath")
        return try {
            val smbFile = diskShare!!.openFile(
                filePath,
                EnumSet.of(AccessMask.GENERIC_READ, AccessMask.GENERIC_EXECUTE),
                fileAttributes,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_DIRECTORY_FILE)
            )
            val inputStream = smbFile.inputStream
            val length = smbFile.fileInformation.standardInformation.endOfFile

            val response = newFixedLengthResponse(
                Response.Status.OK,
                filePath.getMimeType(),
                inputStream,
                length
            )
            response.addHeader("Accept-Ranges", "bytes")
            Timber.i("Serving $filePath")
            response
        } catch (e: Exception) {
            Timber.e("Error: ${e.message}", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Error: ${e.message}"
            )
        }
    }

    override fun stop() {
        runCatching {
            diskShare?.close()
        }.onFailure {
            Timber.e("Error closing share: ${it.message}")
        }

        runCatching {
            sambaSession?.close()
        }.onFailure {
            Timber.e("Error closing session: ${it.message}")
        }

        runCatching {
            sambaConnection?.close()
        }.onFailure {
            Timber.e("Error closing connection: ${it.message}")
        }
        Timber.d("Connection to Samba server closed.")
    }

    private fun connectToSambaServer(
        sambaServerIP: String,
        retryCount: Int = 3,
        delayMillis: Long = 1000L
    ) {
        Timber.e("=============Connecting to Samba server: $sambaServerIP==============")
        serverScope.launch(IO) {
            var attempt = 0
            while (attempt < retryCount) {
                attempt++
                sambaConnection = runCatching {
                    sambaClient.connect(sambaServerIP)
                }.onFailure {
                    Timber.e("Failed to connect to host: $sambaServerIP, error: ${it.message}")
                }.getOrNull()

                sambaSession = runCatching {
                    sambaConnection?.authenticate(AuthenticationContext.anonymous())
                }.onFailure {
                    Timber.e("Failed to authenticate on host: $sambaServerIP, error: ${it.message}")
                }.getOrNull()

                diskShare = runCatching {
                    sambaSession?.connectShare(SAMBA_SERVER_SHARE_NAME) as? DiskShare
                }.onFailure {
                    Timber.e("Failed to connect to share: $SAMBA_SERVER_SHARE_NAME on host $sambaServerIP, error: ${it.message}")
                }.getOrNull()

                if (diskShare == null) {
                    Timber.e("$SAMBA_SERVER_SHARE_NAME is not a DiskShare on host $sambaServerIP or failed to connect")
                    Timber.e("Retrying to connect to share: $SAMBA_SERVER_SHARE_NAME on attempt $attempt/$retryCount in $delayMillis ms")
                    delay(delayMillis)
                } else {
                    Timber.e("=============Successfully connected to share: $SAMBA_SERVER_SHARE_NAME on attempt $attempt/$retryCount==========")
                    break
                }
            }
        }
    }

    private fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).close()
            true
        } catch (e: IOException) {
            false
        }
    }

    fun findAvailablePort(ports: List<Int>): Int? {
        for (port in ports) {
            if (isPortAvailable(port)) {
                return port
            }
        }
        return null
    }
}
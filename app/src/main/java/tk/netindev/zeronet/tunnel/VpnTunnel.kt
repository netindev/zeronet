package tk.netindev.zeronet.tunnel

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

class VpnTunnel(
    private val vpnInterface: ParcelFileDescriptor,
    private val tunnelSocket: Socket,
    private val websocketConnection: WebSocketConnection,
    private val sshConnection: SSHConnection,
    private val onLogMessage: (String) -> Unit
) {
    private val TAG = "VpnTunnel"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isRunning = AtomicBoolean(false)
    
    // Tunnel configuration - SSH server through WebSocket
    private val tunnelServer = "mobile.tcatarina.me"
    private val tunnelPort = 22  // SSH port on the server
    
    fun start() {
        if (isRunning.getAndSet(true)) {
            onLogMessage("Tunnel already running")
            return
        }
        
        onLogMessage("Starting VPN tunnel routing...")
        
        scope.launch {
            try {
                // Start packet processing
                processVpnPackets()
            } catch (e: Exception) {
                onLogMessage("Error in VPN tunnel: ${e.message}")
                stop()
            }
        }
    }
    
    fun stop() {
        if (isRunning.getAndSet(false)) {
            scope.cancel()
            onLogMessage("VPN tunnel stopped")
        }
    }
    
    private suspend fun processVpnPackets() {
        withContext(Dispatchers.IO) {
            try {
                val inputStream = FileInputStream(vpnInterface.fileDescriptor)
                val outputStream = FileOutputStream(vpnInterface.fileDescriptor)
                
                onLogMessage("VPN tunnel packet processing started")
                
                val buffer = ByteArray(32768)
                
                while (isRunning.get()) {
                    try {
                        // Read packet from VPN interface
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead > 0) {
                            val packet = buffer.copyOf(bytesRead)
                            
                            // Process packet through tunnel
                            val response = processPacket(packet)
                            
                            // Write response back to VPN interface
                            if (response != null) {
                                outputStream.write(response)
                                outputStream.flush()
                            }
                        }
                    } catch (e: Exception) {
                        if (isRunning.get()) {
                            onLogMessage("Error processing packet: ${e.message}")
                        }
                    }
                }
                
            } catch (e: Exception) {
                onLogMessage("Error in packet processing: ${e.message}")
            }
        }
    }
    
    private suspend fun processPacket(packet: ByteArray): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                // Parse IP packet
                val ipHeader = parseIpHeader(packet)
                if (ipHeader != null) {
                    // Only process TCP packets
                    if (ipHeader.protocol == 6) { // TCP
                        onLogMessage("Processing TCP packet: ${ipHeader.source} -> ${ipHeader.destination}")
                        
                        // Route packet through SSH tunnel
                        return@withContext routeThroughTunnel(packet, ipHeader)
                    } else {
                        onLogMessage("Ignoring non-TCP protocol: ${ipHeader.protocol}")
                        return@withContext null
                    }
                }
                null
            } catch (e: Exception) {
                onLogMessage("Error processing packet: ${e.message}")
                null
            }
        }
    }
    
    private suspend fun routeThroughTunnel(packet: ByteArray, ipHeader: IpHeader): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                onLogMessage("Routing packet through SSH tunnel: ${ipHeader.source} -> ${ipHeader.destination}")
                
                // 1. Parse the IP packet and extract TCP data
                val tcpData = extractTcpData(packet, ipHeader)
                if (tcpData == null) {
                    onLogMessage("Failed to extract TCP data")
                    return@withContext null
                }
                
                // 2. Send through SSH tunnel to remote server
                val response = sendThroughSshTunnel(tcpData, ipHeader)
                if (response == null) {
                    onLogMessage("No response from SSH tunnel")
                    return@withContext null
                }
                
                // 3. Reconstruct IP packet with response
                val reconstructedPacket = reconstructIpPacket(packet, response, ipHeader)
                
                onLogMessage("Successfully routed packet through SSH tunnel")
                return@withContext reconstructedPacket
                
            } catch (e: Exception) {
                onLogMessage("Error routing through tunnel: ${e.message}")
                null
            }
        }
    }
    
    private fun extractTcpData(packet: ByteArray, ipHeader: IpHeader): ByteArray? {
        return try {
            // Parse IP header length from the first byte
            val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4
            onLogMessage("IP header length: $ipHeaderLength bytes")
            
            // TCP header starts after IP header
            val tcpHeaderOffset = ipHeaderLength
            
            // Check if packet is large enough to contain TCP header
            if (packet.size < tcpHeaderOffset + 20) {
                onLogMessage("Packet too small: ${packet.size} bytes, need at least ${tcpHeaderOffset + 20} bytes")
                return null
            }
            
            // Extract TCP header and data
            val tcpDataLength = packet.size - tcpHeaderOffset
            val tcpData = ByteArray(tcpDataLength)
            System.arraycopy(packet, tcpHeaderOffset, tcpData, 0, tcpDataLength)
            
            // Log TCP header info
            val sourcePort = ((tcpData[0].toInt() and 0xFF) shl 8) or (tcpData[1].toInt() and 0xFF)
            val destPort = ((tcpData[2].toInt() and 0xFF) shl 8) or (tcpData[3].toInt() and 0xFF)
            val tcpFlags = tcpData[13].toInt() and 0x3F
            
            onLogMessage("TCP: ${sourcePort} -> ${destPort} (flags: 0x${tcpFlags.toString(16)})")
            onLogMessage("Extracted TCP data: ${tcpDataLength} bytes")
            
            tcpData
            
        } catch (e: Exception) {
            onLogMessage("Error extracting TCP data: ${e.message}")
            onLogMessage("Packet size: ${packet.size} bytes")
            null
        }
    }
    
    private suspend fun sendThroughSshTunnel(tcpData: ByteArray, ipHeader: IpHeader): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                // Get SSH connection from TunnelManager
                val sshConnection = getSshConnection()
                if (sshConnection == null || !sshConnection.isConnected()) {
                    onLogMessage("SSH connection not available")
                    return@withContext null
                }
                
                // Create SSH channel data packet
                val sshPacket = createSshChannelDataPacket(tcpData)
                
                // Send through WebSocket connection
                val websocketConnection = getWebSocketConnection()
                if (websocketConnection != null) {
                    websocketConnection.send(sshPacket)
                    onLogMessage("Sent ${tcpData.size} bytes through SSH channel")
                    
                    // Read response from WebSocket
                    val response = websocketConnection.receive()
                    if (response != null) {
                        val tcpResponse = extractTcpDataFromSshPacket(response)
                        if (tcpResponse != null) {
                            onLogMessage("Received ${tcpResponse.size} bytes from SSH channel")
                            return@withContext tcpResponse
                        }
                    }
                }
                
                onLogMessage("No response received from SSH tunnel")
                return@withContext null
                
            } catch (e: Exception) {
                onLogMessage("Error sending through SSH tunnel: ${e.message}")
                null
            }
        }
    }
    
    private fun createSshChannelDataPacket(tcpData: ByteArray): ByteArray {
        // SSH_MSG_CHANNEL_DATA packet
        val packet = ByteArrayOutputStream()
        
        // Packet type (94 = SSH_MSG_CHANNEL_DATA)
        packet.write(94)
        
        // Recipient channel (1 = our session channel)
        packet.write(encodeUint32(1))
        
        // Data
        packet.write(encodeString(String(tcpData)))
        
        return packet.toByteArray()
    }
    
    private fun extractTcpDataFromSshPacket(sshPacket: ByteArray): ByteArray? {
        return try {
            if (sshPacket.size < 5) return null
            
            // Check if it's a SSH_MSG_CHANNEL_DATA packet
            if (sshPacket[0].toInt() == 94) {
                // Skip packet type and recipient channel
                val dataStart = 5
                val dataLength = ((sshPacket[1].toInt() and 0xFF) shl 24) or
                                ((sshPacket[2].toInt() and 0xFF) shl 16) or
                                ((sshPacket[3].toInt() and 0xFF) shl 8) or
                                (sshPacket[4].toInt() and 0xFF)
                
                if (dataStart + dataLength <= sshPacket.size) {
                    val tcpData = ByteArray(dataLength)
                    System.arraycopy(sshPacket, dataStart, tcpData, 0, dataLength)
                    return tcpData
                }
            }
            
            null
        } catch (e: Exception) {
            onLogMessage("Error extracting TCP data from SSH packet: ${e.message}")
            null
        }
    }
    
    private fun encodeUint32(value: Int): ByteArray {
        return byteArrayOf(
            (value shr 24).toByte(),
            (value shr 16).toByte(),
            (value shr 8).toByte(),
            value.toByte()
        )
    }
    
    private fun encodeString(str: String): ByteArray {
        val data = str.toByteArray()
        val result = ByteArrayOutputStream()
        result.write(encodeUint32(data.size))
        result.write(data)
        return result.toByteArray()
    }
    
    private fun getSshConnection(): SSHConnection? {
        return sshConnection
    }
    
    private fun getWebSocketConnection(): WebSocketConnection? {
        return websocketConnection
    }
    
    private fun reconstructIpPacket(originalPacket: ByteArray, tcpResponse: ByteArray, ipHeader: IpHeader): ByteArray? {
        return try {
            val ipHeaderLength = (originalPacket[0].toInt() and 0x0F) * 4
            val tcpHeaderOffset = ipHeaderLength
            
            // Calculate new packet size
            val newPacketSize = tcpHeaderOffset + tcpResponse.size
            val reconstructedPacket = ByteArray(newPacketSize)
            
            // Copy IP header
            System.arraycopy(originalPacket, 0, reconstructedPacket, 0, tcpHeaderOffset)
            
            // Swap source and destination IPs
            reconstructedPacket[12] = originalPacket[16]
            reconstructedPacket[13] = originalPacket[17]
            reconstructedPacket[14] = originalPacket[18]
            reconstructedPacket[15] = originalPacket[19]
            reconstructedPacket[16] = originalPacket[12]
            reconstructedPacket[17] = originalPacket[13]
            reconstructedPacket[18] = originalPacket[14]
            reconstructedPacket[19] = originalPacket[15]
            
            // Update IP total length
            val totalLength = newPacketSize
            reconstructedPacket[2] = (totalLength shr 8).toByte()
            reconstructedPacket[3] = totalLength.toByte()
            
            // Copy TCP response data
            System.arraycopy(tcpResponse, 0, reconstructedPacket, tcpHeaderOffset, tcpResponse.size)
            
            // Recalculate IP checksum
            val ipChecksum = calculateIpChecksum(reconstructedPacket, 0, 20)
            reconstructedPacket[10] = (ipChecksum shr 8).toByte()
            reconstructedPacket[11] = ipChecksum.toByte()
            
            onLogMessage("Reconstructed IP packet: ${newPacketSize} bytes")
            reconstructedPacket
            
        } catch (e: Exception) {
            onLogMessage("Error reconstructing IP packet: ${e.message}")
            null
        }
    }
    
    private fun calculateIpChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < offset + length) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }
    

    

    

    

    

    

    
    private fun parseIpHeader(packet: ByteArray): IpHeader? {
        return try {
            if (packet.size < 20) return null
            
            val version = (packet[0].toInt() shr 4) and 0x0F
            if (version != 4) return null // Only IPv4 for now
            
            val headerLength = (packet[0].toInt() and 0x0F) * 4
            val totalLength = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
            val protocol = packet[9].toInt() and 0xFF
            
            val sourceIp = InetAddress.getByAddress(
                byteArrayOf(packet[12], packet[13], packet[14], packet[15])
            )
            val destIp = InetAddress.getByAddress(
                byteArrayOf(packet[16], packet[17], packet[18], packet[19])
            )
            
            IpHeader(
                version = version,
                headerLength = headerLength,
                totalLength = totalLength,
                protocol = protocol,
                source = sourceIp,
                destination = destIp
            )
        } catch (e: Exception) {
            null
        }
    }
    
    data class IpHeader(
        val version: Int,
        val headerLength: Int,
        val totalLength: Int,
        val protocol: Int,
        val source: InetAddress,
        val destination: InetAddress
    )
}

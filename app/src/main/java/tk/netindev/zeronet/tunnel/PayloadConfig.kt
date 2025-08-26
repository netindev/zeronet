package tk.netindev.zeronet.tunnel

object PayloadConfig {
    
    data class PayloadInfo(
        val name: String,
        val payload: String,
        val description: String
    )
    
    val payloads = listOf(
        PayloadInfo(
            name = "TIM - Payload A",
            payload = "CONNECT /-cgi/trace HTTP/1.1[lf]Host: bancah.com.br[crlf][crlf][split][crlf]GET- / HTTP/1.1[crlf]Host: mobile.tcatarina.me[crlf]Upgrade: Websocket[crlf][crlf]",
            description = "TIM - WebSocket SSH Tunnel (HTTP Injector Style)"
        ),
        PayloadInfo(
            name = "VIVO - Payload B", 
            payload = "CONNECT /-cgi/trace HTTP/1.1[lf]Host: bancah.com.br[crlf][crlf][split][crlf]GET- / HTTP/1.1[crlf]Host: mobile.tcatarina.me[crlf]Upgrade: Websocket[crlf][crlf]",
            description = "VIVO - WebSocket SSH Tunnel"
        ),
        PayloadInfo(
            name = "CLARO - Payload C",
            payload = "CONNECT /-cgi/trace HTTP/1.1[lf]Host: bancah.com.br[crlf][crlf][split][crlf]GET- / HTTP/1.1[crlf]Host: mobile.tcatarina.me[crlf]Upgrade: Websocket[crlf][crlf]",
            description = "CLARO - WebSocket SSH Tunnel"
        ),
        PayloadInfo(
            name = "Custom - Payload D",
            payload = "CONNECT /-cgi/trace HTTP/1.1[lf]Host: bancah.com.br[crlf][crlf][split][crlf]GET- / HTTP/1.1[crlf]Host: mobile.tcatarina.me[crlf]Upgrade: Websocket[crlf][crlf]",
            description = "Custom - WebSocket SSH Tunnel"
        )
    )
    
    fun getPayloadByName(name: String): PayloadInfo? {
        return payloads.find { it.name == name }
    }
    
    fun formatPayload(rawPayload: String): String {
        return rawPayload
            .replace("[lf]", "\n")
            .replace("[crlf]", "\r\n")
            .replace("[split]", "\r\n")
    }
}

package tk.netindev.zeronet.data

data class PayloadItem(
    val payloadName: String,
    val payloadString: String,
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val tunnelType: String = "SSH_PROXY"
)

data class RemoteProxyConfig(
    val host: String = "",
    val port: String = ""
)

object PayloadManager {

    private val TIM_PAYLOADS = listOf(
        PayloadItem("WS ZERO-RATED bancah.com.br", "CONNECT /-cgi/trace HTTP/1.1[lf]Host: bancah.com.br[crlf][crlf][split][crlf]GET- / HTTP/1.1[crlf]Host: [host][crlf]Upgrade: Websocket[crlf][crlf]", "bancah.com.br", 80, "SSH_PROXY"),
    )

    private val CLARO_PAYLOADS = listOf(
        PayloadItem("ROTATE ZERO-RATED type1", "HTTP/1.1[lf]HTTP/ [lf]Host: [rotate=minhaclaro.claro.com.br; www.claro.com.br;www.flickr.com;foxnews.com;www.uc.com;tv.com;netfix.com;palcomp3.com;m.operamini.com;m.olx.com.br;www.sky.com;www.waze.com;ff.garena.com;www.hooq.tv;www.terra.com;salesrock.virginmobile.mx;www.yahoo.com;imgur.com;get.adobe.com;youtube.com;google.com;instagram.com;microsoft.com;akamai.net;1.cloudfront.net;www.dropbox.com][lf]", "[host]", 80, "SSH_PROXY")
    )

    private val VIVO_PAYLOADS = listOf(
        PayloadItem("WS ZERO-RATED appstore.vivo.com.br", "ACL / HTTP/1.1[crlf]Host: [host][crlf]Upgrade: Websocket[crlf][crlf]", "www.appstore.vivo.com.br", 80, "SSH_PROXY"),
    )

    fun getOperators(): List<String> = listOf("TIM", "VIVO", "CLARO")

    fun getPayloadsForOperator(operator: String): List<PayloadItem> {
        return when (operator) {
            "TIM" -> TIM_PAYLOADS
            "CLARO" -> CLARO_PAYLOADS
            "VIVO" -> VIVO_PAYLOADS
            "Custom Payload" -> emptyList()
            else -> emptyList()
        }
    }

    fun getPayloadString(operator: String, payloadName: String): String? {
        val payloads = getPayloadsForOperator(operator)
        return payloads.find { it.payloadName == payloadName }?.payloadString
    }

    fun getPayloadItem(operator: String, payloadName: String): PayloadItem? {
        val payloads = getPayloadsForOperator(operator)
        return payloads.find { it.payloadName == payloadName }
    }
}

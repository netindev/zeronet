package tk.netindev.zeronet.data

data class PayloadItem(
    val payloadName: String,
    val payloadString: String,
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val tunnelType: String = "SSH_PROXY",
    val dnsttDnsServer: String = "",
    val dnsttTunnelDomain: String = "",
    val dnsttPublicKey: String = ""
)

data class RemoteProxyConfig(
    val host: String = "",
    val port: String = ""
)

object PayloadManager {

    private val TIM_PAYLOADS = listOf(
        PayloadItem("WS ZERO-RATED bancah.com.br", "CONNECT /-cgi/trace HTTP/1.1[lf]Host: bancah.com.br[crlf][crlf][split][crlf]GET- / HTTP/1.1[crlf]Host: [host][crlf]Upgrade: Websocket[crlf][crlf]", "bancah.com.br", 80, "SSH_PROXY"),
        PayloadItem("DNSTT 189.40.198.81", "", tunnelType = "DNSTT", dnsttDnsServer = "189.40.198.81", dnsttTunnelDomain = "d.dr2.site", dnsttPublicKey = "57b02625a2982f11a073f89a3ac676d81f70e17eea09eed143a7b7dac675d768"),
    )

    private val CLARO_PAYLOADS = listOf(
        PayloadItem("DNSTT 201.82.0.64", "", tunnelType = "DNSTT", dnsttDnsServer = "201.82.0.64", dnsttTunnelDomain = "d.dr2.site", dnsttPublicKey = "57b02625a2982f11a073f89a3ac676d81f70e17eea09eed143a7b7dac675d768"),
        PayloadItem("DNSTT 177.74.185.42", "", tunnelType = "DNSTT", dnsttDnsServer = "177.74.185.42", dnsttTunnelDomain = "d.dr2.site", dnsttPublicKey = "57b02625a2982f11a073f89a3ac676d81f70e17eea09eed143a7b7dac675d768"),
        PayloadItem("DNSTT 181.213.132.2", "", tunnelType = "DNSTT", dnsttDnsServer = "181.213.132.2", dnsttTunnelDomain = "d.dr2.site", dnsttPublicKey = "57b02625a2982f11a073f89a3ac676d81f70e17eea09eed143a7b7dac675d768"),
    )

    private val VIVO_PAYLOADS = listOf(
        PayloadItem("WS ZERO-RATED appstore.vivo.com.br", "ACL / HTTP/1.1[crlf]Host: [host][crlf]Upgrade: Websocket[crlf][crlf]", "www.appstore.vivo.com.br", 80, "SSH_PROXY"),
        PayloadItem("WS ZERO-RATED ethiodragon.sbs", "ACL / [split]HTTP/1.1 [lf]Host: ipv4.ethiodragon.sbs [lf]Upgrade: Websocket[lf][lf]", "ipv4.ethiodragon.sbs", 80, "SSH_PROXY"),
        PayloadItem("WS ZERO-RATED g.whatsapp.net", "POST HTTP/1.1[crlf]Host:g.whatsapp.net[crlf][crlf]\nCONNECT [host_port] [protocol][crlf][crlf]", "ipv4.ethiodragon.sbs", 80, "SSH_PROXY"),
        PayloadItem("WS ZERO-RATED ddivulga.com", "[split]ACL / HTTP/1.1[crlf]Host: ced.vivo.ddivulga.com[crlf]Connection: keep-alive[crlf]User-Agent: ua[crlf]Referer: http://a.vivo.ddivulga.com/index-tag.html[crlf][crlf]", "ipv4.ethiodragon.sbs", 80, "SSH_PROXY"),
        PayloadItem("WS ZERO-RATED recarga.vivo.com.br", "CONNECT [host_port] [protocol][crlf]Host: recarga.vivo.com.br[crlf]Upgrade: websocket[crlf][crlf]", "ipv4.ethiodragon.sbs", 80, "SSH_PROXY"),
        PayloadItem("WS ZERO-RATED whatsapp.com", "[delay_split][crlf]ACL / HTTP/1.1[crlf]Host:www.whatsapp.com[crlf]Upgrade: Upgrade[crlf]Connection: [crlf][crlf]", "ipv4.ethiodragon.sbs", 80, "SSH_PROXY"),
        PayloadItem("WS ROTATE vivo.com.br", "[delay_split][lf]ACL / HTTP/1.1 Websocket [lf]Host: [rotate= portal.vivo.com.br;portalrecarga.vivo.com.br/recarga/home/;meuplano.tim.com.br;www.vivo.com.br;buzzfeed.com;mobile.adobe.com;1.0.0.5;1.1.1.1;buzzfeed.com;myspace.com;mobile.google.com;1.0.0.5;spotify.com;pagamentonline.emis.co.ao;playwaze.com;wazer.com;soundwaze.com;escolas.playwaze.com;www.wazeunlimited.com;bucs.playwaze.com;ftp.chillwaze.com;unpkg.com;c6bank.com.br;helpypro.stoodi.com.br;atendimento.lojadointer.com.br;creatorsupport.deezer.com:80;itsupport.surveymonkey.com;linefriendshelp.zendesk.com;vivo.interflashplusvpnpro.cloud;m2.interflashplusvpnpro.cloud][crlf][crlf]", "ipv4.ethiodragon.sbs", 80, "SSH_PROXY"),
        PayloadItem("WS ROTATE POLL 1.1.1.1", "[split][lf] POLL http://1.1.1.1rotate=buzzfeed.com;mobile.adobe.com;1.0.0.5 HTTP/1.1[crlf]Host: http://1.1.1.1rotate=buzzfeed.com;v;mobile.google.com;1.0.0.5[crlf][crlf]", "ipv4.ethiodragon.sbs", 80, "SSH_PROXY"),
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

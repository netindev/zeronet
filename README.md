# ZeroNet - SSH over WebSocket Tunnel

ZeroNet é uma aplicação Android que implementa um túnel SSH over WebSocket para acessar externamente sem censura, seguindo o mesmo princípio do HTTP Injector.

## Funcionalidades

- **HTTP Proxy → SSH (Custom Payload)**: Implementa o mesmo fluxo do HTTP Injector
- **WebSocket Tunneling**: Estabelece conexão WebSocket através de proxy HTTP
- **SSH Connection**: Conecta via SSH através do túnel WebSocket
- **VPN Service**: Roteia todo o tráfego através do túnel SSH
- **Foreground Service**: Mantém o túnel ativo em background

## Configuração

### Servidor WebSocket
O servidor WebSocket está configurado para rodar em:
- **Host**: mobile.tcatarina.me
- **Porta**: 80

### Dados SSH
- **Host**: mobile.tcatarina.me
- **Usuário**: server
- **Senha**: 9e4NcA0YqvaEt@

### Proxy HTTP
- **Host**: bancah.com.br
- **Porta**: 80
- **Autenticação**: Nenhuma

## Payload

O payload utilizado para estabelecer a conexão WebSocket:

```
CONNECT /-cgi/trace HTTP/1.1[lf]Host: bancah.com.br[crlf][crlf][split][crlf]GET- / HTTP/1.1[crlf]Host: mobile.tcatarina.me[crlf]Upgrade: Websocket[crlf][crlf]
```

## Fluxo de Conexão

1. **Conexão HTTP Proxy**: Conecta ao proxy bancah.com.br:80
2. **Envio do Payload**: Envia o payload customizado para estabelecer WebSocket
3. **Upgrade WebSocket**: Recebe resposta "101 Switching Protocols"
4. **Conexão SSH**: Estabelece conexão SSH através do túnel WebSocket
5. **VPN Service**: Inicia serviço VPN para rotear tráfego

## Estrutura do Projeto

```
app/src/main/java/tk/netindev/zeronet/
├── MainActivity.kt              # Interface principal
├── tunnel/
│   ├── TunnelManager.kt         # Gerenciador principal do túnel
│   ├── WebSocketConnection.kt   # Conexão WebSocket
│   ├── SSHConnection.kt         # Conexão SSH
│   ├── VpnService.kt           # Serviço VPN
│   ├── TunnelService.kt        # Serviço em foreground
│   └── PayloadConfig.kt        # Configuração de payloads
```

## Permissões

O aplicativo requer as seguintes permissões:
- `INTERNET`: Para conexões de rede
- `ACCESS_NETWORK_STATE`: Para verificar estado da rede
- `FOREGROUND_SERVICE`: Para serviço em background
- `FOREGROUND_SERVICE_NETWORK`: Para serviço de rede em background
- `POST_NOTIFICATIONS`: Para notificações do sistema

## Como Usar

1. Selecione o operador (TIM, VIVO, CLARO)
2. Selecione o payload correspondente
3. Clique em "Start" para iniciar o túnel
4. Autorize a conexão VPN quando solicitado
5. O túnel será estabelecido e todo o tráfego será roteado através dele

## Logs

O aplicativo exibe logs detalhados do processo de conexão:
- Status da rede
- Conexão com proxy
- Envio do payload
- Resposta do servidor
- Estabelecimento do WebSocket
- Conexão SSH
- Status da VPN

## Dependências

- **JSch**: Para conexões SSH
- **Java-WebSocket**: Para implementação WebSocket
- **OkHttp**: Para requisições HTTP
- **Kotlin Coroutines**: Para operações assíncronas

## Notas de Segurança

⚠️ **ATENÇÃO**: Este projeto é puramente educacional e deve ser usado apenas para fins de pesquisa e aprendizado sobre protocolos de rede e túneis SSH.

## Licença

Este projeto é fornecido "como está" para fins educacionais.

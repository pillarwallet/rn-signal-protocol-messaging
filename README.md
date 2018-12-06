# React Native module for Signal Protocol messaging

## Module description

Module's purpose is to use Signal Client native libraries and use React Native native bridge approach to control module's methods. It also makes use of original <a href="https://github.com/signalapp/libsignal-protocol-java">Signal Client Java library</a> for Android and <a href="https://github.com/christophhagen/libsignal-protocol-swift">Swift wrapper</a> of original <a href="https://github.com/signalapp/libsignal-protocol-c">Signal Client Objective-C library</a> for iOS.

## Pillar Wallet

Module's main purpose is to integrate Signal Protocol messaging within <a href="https://github.com/pillarwallet/pillarwallet">Pillar Wallet React Native project</a> and therefore this module can contain methods or flows that are explicit to Pillar Wallet only.

This module also contains some modifications (such as message tags) that are implemented both on Signal Client level and <a href="https://github.com/pillarwallet/pillar-signal-backend">forked version</a> of Signal Server (origin Toshi Signal Server that no longer exist as Toshi).

Pillar Wallet Signal Protocol messaging in flow chart – https://swimlanes.io/u/DKFDljAqR.

## Versioning

Newest version can be found in Pillar Wallet JFrog repository manager. It is automatically deployed and versioned once module's repository master branch updated.

## Available methods

Methods can be called within React Native. Basic usage in Pillar Wallet:
```
import ChatService from 'services/chat';
const chat = new ChatService();

const chats = await chat.client.getExistingMessages('chat').then(JSON.parse).catch(() => []);

```

Where `getExistingMessages` and its param `chat` can be any method and with its required params below.

### init(config)

This must be called to setup React Native bridge class. Where `config` param. In Pillar Wallet this is already being called on `onboardingActions` so no need to call it anywhere again before calling any method in this project.

It also checks:
- If Signal module locally saved username matches the one that is provided in `config` and if not then it deletes all module storage and performs method `registerAccount()`. After ``
- How many Pre Keys are in device and generates new if amount is below 11.

Required **config** values as JSON object:
```
{
  host: ""
  username: "",
  password: ""
}
```

Where `host` is url to Signal Server, `username`, `password` – user credentials in Signal Server.

### registerAccount()

Registers account on Signal Server (provided username is saved locally), generates Identity Key with Pre Keys, sends keys to Signal Server and saves locally.

### resetAccount()

### addContact(username)

### deleteContact(username)

### deleteContactMessages(username, tag)

### receiveNewMessagesByContact(username, tag)

### getMessagesByContact(username, tag)

### getUnreadMessagesCount(tag)

### getExistingMessages(tag)

### sendSilentMessageByContact(tag, config)

### sendMessageByContact(tag, config)

### setFcmId(fcmId)

## Development

## Other helpful resources on Signal

Signal Protocol:
- Original Signal Server API documentation (might lack some of information or contain deprecated) https://-github.com/signalapp/Signal-Server/wiki/API-Protocol<br/>
- Great video about Signal Protocol in detail https://www.youtube.com/watch?v=VM2VW5WETVM<br/>
- Signal Protocol group messaging https://signal.org/blog/private-groups<br/>
- Some article https://github.com/whereat/wiki/wiki/Should-We-Implement-the-Signal-Protocol<br/>

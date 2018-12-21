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

const getChatsAction = async () => {
  const chats = await chat.client.getExistingMessages('chat').then(JSON.parse).catch(() => []);
}
```

Where `getExistingMessages` is method with its required param `'chat'`.

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

Registers account on Signal Server using username and password provided by `init()` method. After successfull registration username is saved locally to identify Signal user on identity check after each reset. Finally it generates Identity Key with Pre Keys and sends them to Signal Server while also saving a copy locally.

### resetAccount()

Deletes any entries in **device local storage** that was used by Signal Client, including all keys and registration details.

### addContact(username)

Requests remote Pre Keys of provided `username` and saves them in local storage to establish a session with the `username`.

### deleteContact(username)

Deletes all keys, sessions and any other entries in **device local storage** that matches provided `username`.

### sendMessageByContact(tag, config)

Sends encrypted message of provided string and saves unencrypted version of message in **device local storage**. Method contains parameter `tag` which allows separate message categories. Most common `tag` for regular message is `chat` which is used for basic Pillar Wallet chat messages, other available tag is `tx-note` which is used to send transaction notes.

Required **config** values as JSON object:
```
{
  username: ""
  message: "",
  userId: "",
  userConnectionAccessToken: ""
}
```

Where `username` is the username of receiver, `message` is unencrypted basic text of actual message, `userId` is receiver ID on Pillar Wallet Core Platform and `userConnectionAccessToken` is sender and receiver connection access token on Pillar Wallet Core Platform.

Pillar Wallet Core Platform `userId` and `userConnectionAccessToken` are used to identify sender connection status on receiver end whether receiver muted, blocked or disconnected from sender. When message hits Signal Server it acts according to received connection status from Pillar Wallet Core Platform.

### sendSilentMessageByContact(tag, config)

This method has same functionality as `sendMessageByContact()` just if message is sent using this method then receiver will not receive any Push Notification about new message received.

### receiveNewMessagesByContact(username, tag)

Checks any messages in remote Signal Server queue that are received from provided `username` and under provided `tag`. Received messages are decoded, stored in **device local storage** and returned as array of messages.

Returned (promise) JSON object:
```
{
  messages: [
    {
      username: "",
      device: 1,
      serverTimestamp: 0,
      savedTimestamp: 0,
      content: "",
      type: "",
      status: ""
    }
    ...
  ]
}
```

Returned JSON object contains key `messages` that contains JSON array of messages. Message JSON object itself contains `username` which can be either sender or responder username (messaging history), `device` is Signal Server device ID which in Pillar Wallet case stays as value `1`, `serverTimestamp` is the timestamp from server when the message was sent while `savedTimestamp` stands for timestamp when the message was decoded and saved locally, `content` is decoded message text content.

Message object can also be error message, in this case object keys `type` and `status` is used. Type can be either `message` (regular) or `warning` (error). Status is returned only once type is warning and status value can be optional and if it exist it can only contain value of `UNDECRYPTABLE_MESSAGE`.

### getMessagesByContact(username, tag)

Returns message history with provided `username` and `tag`.

Returned (promise) messages JSON array:
```
[
  {
    username: "",
    device: 1,
    serverTimestamp: 0,
    savedTimestamp: 0,
    content: "",
    type: "",
    status: ""
  }
  ...
]
```

Message object itself contains values described under `receiveNewMessagesByContact()` method.

### getUnreadMessagesCount(tag)

Requests user's message queue by `tag` from Signal Server and returns mapped array of senders that user has session with with a count of messages in the queue from each sender.

Returned (promise) JSON object:
```
{
  unread: {
    "sender-username": {
      count: 1,
      latest: 0
    }
    ...
  }
}
```

Where `unread` is returned JSON top key, `"sender-username"` is sender username and more can be at the same object level, `count` is number of messages in the queue from sender and `latest` is the timestamp of latest message from this sender.

### getExistingMessages(tag)

Returns all senders with either their last message or all messages for `tag` that are already fetched and stored in local storage.

Returned (promise) JSON array for tag `chat`:
```
[
  {
    username: "",
    lastMessage:  {
      username: "",
      device: 1,
      serverTimestamp: 0,
      savedTimestamp: 0,
      content: "",
      type: "",
      status: ""
    }
  }
  ...
]
```

Where `username` contains value of sender username and `lastMessage` contains object of last message from chat with this user, this can be either received or sent message. Message object itself contains values described under `receiveNewMessagesByContact()` method.

Message object can also be error message, in this case object keys `type` and `status` is used. Type can be either `message` (regular) or `warning` (error). Status is returned only once type is warning and status value can be optional and if it exist it can only contain value of `UNDECRYPTABLE_MESSAGE`.

Returned (promise) JSON array for other tags:
```
[
  {
    username: "",
    messages:  [
      {
        username: "",
        device: 1,
        serverTimestamp: 0,
        savedTimestamp: 0,
        content: "",
        type: "",
        status: ""
      }
      ...
    ]
  }
  ...
]
```

Other tags contain whole array of messages under for chat history with user under user object key `messages`, while message JSON object keys remain the same as for `chat` tag response above and values explained under `receiveNewMessagesByContact()` method. Messages in the array can be both from sender and responder since it returns full history.

### deleteContactMessages(username, tag)

Deletes message entries in **device local storage** that matches provided `username` and `tag`.

### setFcmId(fcmId)

Sends user's Firebase Cloud Messaging (FCM) `fcmId` (ID) value to Signal Server in order to get Push Notifications when new Signal message is received to the user's queue.

## Other helpful resources on Signal

Signal Protocol:
- Original Signal Server API documentation (might lack some of information or contain deprecated) https://-github.com/signalapp/Signal-Server/wiki/API-Protocol<br/>
- Great video about Signal Protocol in detail https://www.youtube.com/watch?v=VM2VW5WETVM<br/>
- Signal Protocol group messaging https://signal.org/blog/private-groups<br/>
- Some article https://github.com/whereat/wiki/wiki/Should-We-Implement-the-Signal-Protocol<br/>

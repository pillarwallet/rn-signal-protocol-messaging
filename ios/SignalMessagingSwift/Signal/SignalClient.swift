//
//  SignalClient.swift
//  PillarWalletSigal
//
//  Created by Mantas on 05/07/2018.
//  Copyright © 2018 Pillar. All rights reserved.
//

import UIKit
import SignalProtocol

class SignalClient: NSObject {

    public let MESSAGE_TYPE_CIPHERTEXT: Int = 1;

    private let signalServer: SignalServer
    private let username: String
    private let host: String

    init(username: String, accessToken: String, host: String) {
        self.username = username;
        self.host = host
        self.signalServer = SignalServer(accessToken: accessToken, host: host)

        super.init()
    }

    func store() -> SignalStore? {
        do {
            let store = try SignalStore(identityKeyStore: IdentityKeyStorePillar(), preKeyStore: PreKeyStorePillar(), sessionStore: SessionStorePillar(), signedPreKeyStore: SignedPreKeyStorePillar(), senderKeyStore: SenderKeyStorePillar())
            return store
        } catch {
            print("error init store")
        }

        return nil
    }

    func register(success: @escaping (_ success: String) -> Void, failure: @escaping (_ error: String, _ message: String) -> Void) {
        guard let store = self.store() else {
            failure(ERR_NATIVE_FAILED, "Store is invalid")
            return
        }
        
        var parameters = [String : Any]()

        guard store.identityKeyStore.localRegistrationId() == nil else {
            var signalingKey = ProtocolStorage().getSignalingKey();
            if (signalingKey.isEmpty) {
                // updating signalingKey on migration to websocket messaging
                signalingKey = self.generateRandomBytes()
                ProtocolStorage().storeSignalingKey(signalingKey: signalingKey)
            }
            parameters["signalingKey"] = signalingKey
            parameters["fetchesMessages"] = true
            parameters["registrationId"] = ProtocolStorage().getLocalRegistrationId()
            parameters["name"] = ProtocolStorage().getLocalUsername()
            parameters["voice"] = false
            self.signalServer.call(urlPath: URL_ACCOUNTS + "/attributes", method: .PUT, parameters: parameters, success: { (dict) in
                success("ok")
            }) { (error) in
                failure(ERR_SERVER_FAILED, "\(error)")
            }
            return;
        }

        store.identityKeyStore.destroy()

        var registrationId: UInt32

        do {
            registrationId =  try Signal.generateRegistrationId()
        } catch {
            failure(ERR_NATIVE_FAILED, "\(error)")
            return
        }
        
        let signalingKey = self.generateRandomBytes()

        ProtocolStorage().storeLocalRegistrationId(registrationId: registrationId)
        ProtocolStorage().storeLocalUsername(username: self.username)
        ProtocolStorage().storeSignalingKey(signalingKey: signalingKey)

        parameters["signalingKey"] = signalingKey
        parameters["fetchesMessages"] = true
        parameters["registrationId"] = registrationId
        parameters["name"] = self.username
        parameters["voice"] = false

        self.signalServer.call(urlPath: URL_ACCOUNTS, method: .PUT, parameters: parameters, success: { (dict) in
            do {
                try self.registerPreKeys(store: store, start: 1, count: 100, success: { (response) in
                    success(response)
                }, failure: { (error, message) in
                    failure(error, message)
                })
            } catch {
                failure(ERR_NATIVE_FAILED, "\(error)")
            }
        }) { (error) in
            failure(ERR_SERVER_FAILED, "\(error)")
        }
    }

    private func currentTimestamp() -> Int64 {
        return Int64(Date().timeIntervalSince1970)
    }

    private func generateRandomBytes() -> String {
        var keyData = Data(count: 52)
        let result = keyData.withUnsafeMutableBytes {
            SecRandomCopyBytes(kSecRandomDefault, 52, $0)
        }
        if result == errSecSuccess {
            return keyData.base64EncodedString()
        } else {
            print("Problem generating random bytes")
            return ""
        }
    }

    private func registerPreKeys(store: SignalStore, start: UInt32, count: Int, success: @escaping (_ success: String) -> Void, failure: @escaping (_ error: String, _ message: String) -> Void) throws {
        var identityKeyPair: KeyPair? = nil
        do {
            identityKeyPair = try ProtocolStorage().getIdentityKeyPair()
        } catch {
            print("No identityKeyPair in storage, will proceed on creating new one")
        }
            
        if (identityKeyPair == nil){
            identityKeyPair = try Signal.generateIdentityKeyPair()
            ProtocolStorage().storeIdentityKeyPair(keyPair: identityKeyPair!)
        }
        
        var lastRecordKey: SessionPreKey?
        let lastPreKeyIndex = UInt32(GL_MEDIUM_INT)
        do {
            let lastRecordKeyData = store.preKeyStore.load(preKey: lastPreKeyIndex)
            if (lastRecordKeyData != nil) {
                lastRecordKey = try SessionPreKey(from: lastRecordKeyData!)
            } else {
                lastRecordKey = try Signal.generatePreKeys(start: lastPreKeyIndex, count: 1).first
            }
        } catch {
            print("Last recrod key Error info: \(error)")
        }
        
        let preKeys = try Signal.generatePreKeys(start: start, count: count)

        // store pre keys
        let parameters = try preKeys.map { (preKey) -> [String : Any] in
            _ = store.preKeyStore.store(preKey: try preKey.data(), for: preKey.id)
            return ["keyId" : preKey.id, "publicKey" : preKey.keyPair.publicKey.base64EncodedString()]
        }

        // store signed pre key
        let signedPreKey: SessionSignedPreKey = try Signal.generate(signedPreKey: 1, identity: identityKeyPair!, timestamp: 0)
        _ = store.signedPreKeyStore.store(signedPreKey: try signedPreKey.data(), for: signedPreKey.id)

        var requestJSON = [String : Any]()

        guard let lastResortKey = lastRecordKey else {
            failure(ERR_NATIVE_FAILED, "lastResortKey is nil")
            return
        }

        requestJSON["lastResortKey"] = ["keyId" : lastResortKey.id, "publicKey" : lastResortKey.keyPair.publicKey.base64EncodedString()]
        requestJSON["preKeys"] = parameters
        requestJSON["identityKey"] = identityKeyPair?.publicKey.base64EncodedString()
        requestJSON["signedPreKey"] = ["keyId" : signedPreKey.id, "publicKey" : signedPreKey.keyPair.publicKey.base64EncodedString(), "signature" : signedPreKey.signature.base64EncodedString()]

        self.signalServer.call(urlPath: URL_KEYS, method: .PUT, parameters: requestJSON, success: { (dict) in
            success("ok")
        }) { (error) in
            failure(ERR_SERVER_FAILED, "\(error)")
        }
    }

    func checkPreKeys() {
        self.signalServer.call(urlPath: URL_KEYS, method: .GET, success: { (dict) in
            let count = dict["count"] as? Int ?? 0
    
            guard let store = self.store(), count <= 10 else {
                return
            }

            let preKeysNeeded = 100 - count
            let lastPreKeyIndex = PreKeyStorePillar().getLastPreKeyIndex()
            
            do {
                try self.registerPreKeys(store: store, start: lastPreKeyIndex+1, count: preKeysNeeded+1, success: { (success) in
                    
                }, failure: { (result, error) in

                })
            } catch {
            }
        }) { (error) in

        }
    }

    func requestPreKeys(username: String, userId: String, userConnectionAccessToken: String, success: @escaping (_ success: String) -> Void, failure: @escaping (_ error: String, _ message: String) -> Void) {
        var callUrl = URL_KEYS + "/" + username + "/1";
        if (userId != nil && userConnectionAccessToken != nil && !userId.isEmpty && !userConnectionAccessToken.isEmpty) {
            callUrl.append("?userId=" + userId + "&userConnectionAccessToken=" + userConnectionAccessToken);
        }
        self.signalServer.call(urlPath: callUrl, method: .GET, success: { (dict) in
            if let devices = dict["devices"] as? [[String : Any]] {
                if let identityKey: String = dict["identityKey"] as? String,
                    let identityData = Data(base64Encoded: identityKey),
                    let firstDevice = devices.first as? [String : Any],

                    let deviceId = firstDevice["deviceId"] as? Int,
                    let registrationId = firstDevice["registrationId"] as? Int,

                    let preKey = firstDevice["preKey"] as? [String : Any],
                    let preKeyId = preKey["keyId"] as? Int,
                    let preKeyDataString = preKey["publicKey"] as? String,
                    let preKeyData = Data(base64Encoded: preKeyDataString),

                    let signedPreKey = firstDevice["signedPreKey"] as? [String : Any],
                    let signedPreKeyId = signedPreKey["keyId"] as? Int,
                    let signedPreKeyDataString = signedPreKey["publicKey"] as? String,
                    let signedPreKeyData = Data(base64Encoded: signedPreKeyDataString),

                    let signatureString = signedPreKey["signature"] as? String,
                    let signature = Data(base64Encoded: signatureString) {

                    let bundle = SessionPreKeyBundle(registrationId: UInt32(registrationId), deviceId: 1, preKeyId: UInt32(preKeyId), preKey: preKeyData, signedPreKeyId: UInt32(signedPreKeyId), signedPreKey: signedPreKeyData, signature: signature, identityKey: identityData)

                    guard let store = self.store() else {
                        failure(ERR_NATIVE_FAILED, "store failed")
                        return
                    }

                    let address = SignalAddress(name: username, deviceId: 1)
                    
                    // force delete: anytime when method is requested it will add new Identity Key and new Pre Key
                    ProtocolStorage().removeRemoteIdentity(for: address)
                    store.sessionStore.deleteSession(for: address)
                    
                    let sessionBuilder = SessionBuilder(for: address, in: store)

                    do {
                        try sessionBuilder.process(preKeyBundle: bundle)
                    } catch {
                        failure(ERR_NATIVE_FAILED, "\(error)")
                        return
                    }

                    success("ok")
                } else {
                    failure(ERR_SERVER_FAILED, "wrong data received")
                }
            }
        }) { (error) in
            let errorCheck = error as NSError?
            if errorCheck != nil, errorCheck?.code == 404 {
                failure(ERR_ADD_CONTACT_FAILED, "User \(username) doesn't exist.")
            } else {
                failure(ERR_SERVER_FAILED, "\(error)")
            }
        }
    }

    func getContactMessages(username: String, messageTag: String, decodeAndSave: Bool, success: @escaping (_ success: [String : Any]) -> Void, failure: @escaping (_ error: String, _ message: String) -> Void) {
        self.signalServer.call(urlPath: URL_MESSAGES, method: .GET, success: { (dict) in
            let dictionary = self.parseMessages(username: username, messageTag: messageTag, decodeAndSave: decodeAndSave, messagesDictionary: dict)
            success(dictionary)
        }) { (error) in
            failure(ERR_SERVER_FAILED, "\(error)")
        }
    }

    func getAllContactMessages(messageTag: String) -> [[String : Any]] {
        var allMessages = [[String : Any]]()
        MessagesStorage().getAllMessages(for: messageTag).forEach { (key, value) in
            var messageDict = [String : Any]()
            messageDict["username"] = key
            messageDict["unread"] = 0
            
            if (messageTag == "chat"){
                // TODO: needs swift code refactoring in order to parse from object value (messages) above?
                if let dict = MessagesStorage().getMessages(for: key, tag: messageTag).last?.dictionary {
                    messageDict["lastMessage"] = dict
                }
            } else {
                messageDict["messages"] = value
            }

            allMessages.append(messageDict)
        }

        return allMessages
    }

    func saveFCMId(fcmId: String, success: @escaping () -> Void, failure: @escaping (_ error: String, _ message: String) -> Void) {
        guard !fcmId.isEmpty && fcmId != nil else {
            failure(ERR_SERVER_FAILED, "FCM id is empty or null")
            return
        }

        self.signalServer.call(urlPath: URL_GCM, method: .PUT, parameters: ["gcmRegistrationId" : fcmId], success: { (result) in
            success()
        }) { (error) in
            failure(ERR_SERVER_FAILED, "\(error)")
        }
    }

    private func parseMessages(username: String, messageTag: String, decodeAndSave: Bool, messagesDictionary: [String : Any]) -> [String : Any] {
        
        guard let store = self.store() else {
            print(ERR_NATIVE_FAILED)
            return [String : Any]()
        }

        guard let messages = messagesDictionary["messages"] as? [[String : Any]] else {
            print(ERR_SERVER_FAILED)
            return [String : Any]()
        }

        var parsedMessages = [ParsedMessageDTO]()
        var unread = [String : [String : Any]]()

        messages.forEach { (messageDict) in
            let message = MessageDTO(dictionary: messageDict)
            if (messageTag == message.tag){
                let serverTimestamp = message.timestamp
                let address = SignalAddress(name: username, deviceId: 1)
                
                // empty username check is needed because unread message count method doesn't require username
                if MESSAGE_TYPE_CIPHERTEXT == message.type, username.isEmpty {
                    var unreadSource = unread[message.source] ?? [String : Any]()
                    var currentCount = unreadSource["count"] as? Int ?? 0
                    currentCount += 1
                    let latestTimestamp = unreadSource["latest"] as? Int ?? 0
                    unreadSource["count"] = currentCount
                    unreadSource["latest"] = serverTimestamp > latestTimestamp ? serverTimestamp : latestTimestamp;
                    unread[message.source] = unreadSource
                }
                
                var isDuplicateMessage: Bool = false;

                // TODO: understand why it works this way
                // When a user send a message to the interlocutor, he also receives the message
                // (the message data is empty in this case).
                if username == message.source && message.messageData()?.isEmpty == false,
                    decodeAndSave,
                    store.sessionStore.containsSession(for: address),
                    let data = message.messageData() {
                    
                    let sessionCipher = SessionCipher(for: address, in: store)

                    let parsedMessage = ParsedMessageDTO()
                    parsedMessage.username = message.source
                    parsedMessage.device = 1
                    parsedMessage.serverTimestamp = serverTimestamp
                    parsedMessage.savedTimestamp = self.currentTimestamp()
                    parsedMessage.type = "message"

                    var preKeyData: Data? = nil
                    do {
                        let cipher = CiphertextMessage(type: .signal, message: data)
                        preKeyData = try sessionCipher.decrypt(message: cipher)
                    } catch SignalError.duplicateMessage {
                        isDuplicateMessage = true;
                    } catch {
                        do {
                            let cipher = CiphertextMessage(type: .preKey, message: data)
                            preKeyData = try sessionCipher.decrypt(message: cipher)
                        } catch SignalError.untrustedIdentity {
                            ProtocolStorage().removeRemoteIdentity(for: address)
                            do {
                                let cipher = CiphertextMessage(type: .preKey, message: data)
                                preKeyData = try sessionCipher.decrypt(message: cipher)
                            } catch SignalError.duplicateMessage {
                                isDuplicateMessage = true;
                            } catch {
                                print(ERR_NATIVE_FAILED)
                                print("Error info: \(error)")
                            }
                        } catch SignalError.duplicateMessage {
                            isDuplicateMessage = true;
                        } catch {
                            print(ERR_NATIVE_FAILED)
                            print("Error info: \(error)")
                        }
                    }

                    if !isDuplicateMessage {
                        if preKeyData != nil  {
                            if let receivedMessage = String(data: preKeyData!, encoding: .utf8) {
                                parsedMessage.content = receivedMessage
                            }

                        } else {
                            parsedMessage.content = "🔒 You cannot read this message."
                            parsedMessage.type = "warning"
                            parsedMessage.status = "UNDECRYPTABLE_MESSAGE"
                        }

                        parsedMessages.append(parsedMessage)
                        MessagesStorage().save(message: parsedMessage, for: username, tag: messageTag)
                
                        self.signalServer.call(urlPath: "\(URL_MESSAGES)/\(username)/\(message.timestamp)", method: .DELETE, success: { (response) in }, failure: { (error) in })
                    }
                }
            }
        }

//        MessagesStorage().saveUnreadCount(for: username, count: parsedMessages.count)

        var finalDict = [String : Any]()
        finalDict["unread"] = unread
        if (parsedMessages.count > 0) {
            finalDict["messages"] = parsedMessages
        }

        return finalDict
    }
    
    func deleteContactPendingMessages(username: String, messageTag: String, success: @escaping (_ success: String) -> Void, failure: @escaping (_ error: String, _ message: String) -> Void) {
        self.signalServer.call(urlPath: URL_MESSAGES, method: .GET, success: { (dict) in
            guard let messages = dict["messages"] as? [[String : Any]] else {
                success("ok") // no messages object, all good
                return
            }
            
            messages.forEach { (messageDict) in
                let message = MessageDTO(dictionary: messageDict)
                if username == message.source && (messageTag == message.tag || messageTag == "*") {
                    self.signalServer.call(urlPath: "\(URL_MESSAGES)/\(username)/\(message.timestamp)", method: .DELETE, success: { (response) in }, failure: { (error) in })
                }
            }
            
            success("ok")
        }) { (error) in
            failure(ERR_SERVER_FAILED, "\(error)")
        }
    }

    func sendMessage(username: String, messageString: String, userId: String, userConnectionAccessToken: String, messageTag: String, silent: Bool, success: @escaping (_ success: String) -> Void, failure: @escaping (_ error: String, _ message: String) -> Void) {

        let params = self.prepareApiBody(username: username, messageString: messageString, userId: userId, userConnectionAccessToken: userConnectionAccessToken, messageTag: messageTag, silent: silent, failure: { (error) in
            failure(ERR_NATIVE_FAILED, "\(error)")
        });
        
        if (params.isEmpty) {
            return
        }

        self.signalServer.call(urlPath: URL_MESSAGES + "/" + username, method: .PUT, parameters: params, success: { (dict) in
            if dict.count != 0 && dict["staleDevices"] != nil {
                // staleDevices found, request new user PreKey and retry message send
                self.requestPreKeys(username: username, userId: userId, userConnectionAccessToken: userConnectionAccessToken, success: { _ in
                    self.sendMessage(username: username, messageString: messageString, userId: userId, userConnectionAccessToken: userConnectionAccessToken, messageTag: messageTag, silent: silent, success: { (message) in
                        success(message)
                    }, failure: { (code, error) in
                        failure(code, "\(error)")
                    })
                }, failure: { (code, error) in
                    failure(code, "\(error)")
                })
            } else {
                self.saveSentMessage(messageTag: messageTag, username: self.username, messageString: messageString, timestamp: self.currentTimestamp());
                success("ok")
            }
        }) { (error) in
            failure(ERR_SERVER_FAILED, "\(error)")
        }
    }
    
    func prepareApiBody(username: String, messageString: String, userId: String, userConnectionAccessToken: String, messageTag: String, silent: Bool, failure: @escaping (_ message: String) -> Void) -> [String : Any] {
        guard let store = self.store() else {
            failure("Store is invalid")
            return [:]
        }
        
        let address = SignalAddress(name: username, deviceId: 1)
        let sessionCipher = SessionCipher(for: address, in: store)
        var cipherTextMessage: CiphertextMessage
        var remoteRegistrationId: UInt32
        guard let data = messageString.data(using: .utf8) else {
            failure("String encoding to bytes failed")
            return [:]
        }
        do {
            cipherTextMessage = try sessionCipher.encrypt(data)
            remoteRegistrationId = try sessionCipher.getRemoteRegistrationId();
        } catch {
            failure("\(error)")
            return [:]
        }
        var message = [String : Any]()
        message["type"] = 1
        message["destination"] = username
        message["content"] = ""
        message["tag"] = messageTag
        if (userId != nil && !userId.isEmpty){
            message["userId"] = userId
        }
        if (userConnectionAccessToken != nil && !userConnectionAccessToken.isEmpty){
            message["userConnectionAccessToken"] = userConnectionAccessToken
        }
        message["silent"] = silent
        message["destinationDeviceId"] = 1
        message["destinationRegistrationId"] = remoteRegistrationId
        message["body"] = cipherTextMessage.message.base64EncodedString()
        return ["messages" : [message]]
    }
    
    func saveSentMessage(messageTag: String, username: String, messageString: String, timestamp: Int64) -> Void {
        let parsedMessage = ParsedMessageDTO()
        parsedMessage.username = self.username
        parsedMessage.device = 1
        parsedMessage.serverTimestamp = timestamp * 1000
        parsedMessage.savedTimestamp = timestamp
        parsedMessage.content = messageString
        MessagesStorage().save(message: parsedMessage, for: username, tag: messageTag)
    }
    
    func decryptReceivedBody(body: String) -> NSData {
        let legacyMessage = LegacyMessage(body: body, signalingKey: ProtocolStorage().getSignalingKey())
        return legacyMessage.serialized as NSData
    }
    
    func decryptSignalMessage(messageTag: String, receivedMessage: String, success: @escaping (_ message: String) -> Void, failure: @escaping (_ message: String) -> Void) -> Void {
        
        guard let store = self.store() else {
            failure("No store found")
            return
        }
    
        var parsedMessages = [ParsedMessageDTO]()
        var isDuplicateMessage: Bool = false;
        
        let data = receivedMessage.data(using: .utf8)
        let decoded : Any;
        
        do {
            decoded = try JSONSerialization.jsonObject(with: data ?? Data(), options: .mutableContainers)
        } catch {
            failure("Decoding error: \(error.localizedDescription)");
            return
        }
        guard let dictFromJSON = decoded as? [String: Any] else {
            failure("Failed to parse JSON")
            return
        }
        
        let message = MessageDTO(dictionary: dictFromJSON)
        let serverTimestamp = message.timestamp
        let username = message.source
        let address = SignalAddress(name: username, deviceId: 1)
        
        if store.sessionStore.containsSession(for: address),
            let data = message.messageData() {
            
            let sessionCipher = SessionCipher(for: address, in: store)
            
            let parsedMessage = ParsedMessageDTO()
            parsedMessage.username = message.source
            parsedMessage.device = 1
            parsedMessage.serverTimestamp = serverTimestamp
            parsedMessage.savedTimestamp = self.currentTimestamp()
            parsedMessage.type = "message"
            
            var preKeyData: Data? = nil
            do {
                let cipher = CiphertextMessage(type: .signal, message: data)
                preKeyData = try sessionCipher.decrypt(message: cipher)
            } catch SignalError.duplicateMessage {
                isDuplicateMessage = true;
            } catch {
                do {
                    let cipher = CiphertextMessage(type: .preKey, message: data)
                    preKeyData = try sessionCipher.decrypt(message: cipher)
                } catch SignalError.untrustedIdentity {
                    ProtocolStorage().removeRemoteIdentity(for: address)
                    do {
                        let cipher = CiphertextMessage(type: .preKey, message: data)
                        preKeyData = try sessionCipher.decrypt(message: cipher)
                    } catch SignalError.duplicateMessage {
                        isDuplicateMessage = true;
                    } catch {
                        print(ERR_NATIVE_FAILED)
                        failure("Error info: \(error)")
                        return
                    }
                } catch SignalError.duplicateMessage {
                    isDuplicateMessage = true;
                } catch {
                    print(ERR_NATIVE_FAILED)
                    failure("Error info: \(error)")
                    return
                }
            }
            
            if !isDuplicateMessage {
                if preKeyData != nil  {
                    if let receivedMessage = String(data: preKeyData!, encoding: .utf8) {
                        parsedMessage.content = receivedMessage
                    }
                    
                } else {
                    parsedMessage.content = "🔒 You cannot read this message."
                    parsedMessage.type = "warning"
                    parsedMessage.status = "UNDECRYPTABLE_MESSAGE"
                }
                
                parsedMessages.append(parsedMessage)
                MessagesStorage().save(message: parsedMessage, for: username, tag: messageTag)
            }
        }
        success("ok");
    }
    
    func deleteSignalMessage(username: String, timestamp: NSInteger, success: @escaping (_ message: String) -> Void) -> Void {
        self.signalServer.call(urlPath: "\(URL_MESSAGES)/\(username)/\(timestamp)", method: .DELETE, success: { (response) in }, failure: { (error) in })
        success("ok")
    }
    
}

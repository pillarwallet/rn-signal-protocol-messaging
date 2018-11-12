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
    private let password: String
    private let host: String

    init(username: String, password: String, host: String) {
        self.username = username
        self.password = password
        self.host = host
        self.signalServer = SignalServer(username: username, password: password, host: host)

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

        guard store.identityKeyStore.localRegistrationId() == nil else {
            success("ok")
            return
        }

        store.identityKeyStore.destroy()

        var registrationId: UInt32

        do {
            registrationId =  try Signal.generateRegistrationId()
        } catch {
            failure(ERR_NATIVE_FAILED, "\(error)")
            return
        }

        ProtocolStorage().storeLocalRegistrationId(registrationId: registrationId)
        ProtocolStorage().storeLocalUsername(username: self.username)

        var parameters = [String : Any]()
        let signalingKey = self.generateRandomBytes()

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

    func requestPreKeys(username: String, success: @escaping (_ success: String) -> Void, failure: @escaping (_ error: String, _ message: String) -> Void) {
        self.signalServer.call(urlPath: URL_KEYS + "/" + username + "/1", method: .GET, success: { (dict) in
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

    func getContactMessages(username: String, decodeAndSave: Bool, success: @escaping (_ success: [String : Any]) -> Void, failure: @escaping (_ error: String, _ message: String) -> Void) {
        self.signalServer.call(urlPath: URL_MESSAGES, method: .GET, success: { (dict) in
            let dictionary = self.parseMessages(username: username, decodeAndSave: decodeAndSave, messagesDictionary: dict)
            success(dictionary)
        }) { (error) in
            failure(ERR_SERVER_FAILED, "\(error)")
        }
    }

    func getAllContactMessages() -> [[String : Any]] {
        var allMessages = [[String : Any]]()
        MessagesStorage().getAllMessages().forEach { (key, value) in
            var messageDict = [String : Any]()
            messageDict["username"] = key
            messageDict["unread"] = 0
            if let dict = MessagesStorage().getMessages(for: key).last?.dictionary {
                messageDict["lastMessage"] = dict
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

    private func parseMessages(username: String, decodeAndSave: Bool, messagesDictionary: [String : Any]) -> [String : Any] {
        
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
                    MessagesStorage().save(message: parsedMessage, for: username)
            
                    self.signalServer.call(urlPath: "\(URL_MESSAGES)/\(username)/\(message.timestamp)", method: .DELETE, success: { (response) in }, failure: { (error) in })
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

    func sendMessage(username: String, messageString: String, success: @escaping (_ success: String) -> Void, failure: @escaping (_ error: String, _ message: String) -> Void) {
        guard let store = self.store() else {
            failure(ERR_NATIVE_FAILED, "Store is invalid")
            return
        }

        let address = SignalAddress(name: username, deviceId: 1)
        let sessionCipher = SessionCipher(for: address, in: store)
        var cipherTextMessage: CiphertextMessage
        var remoteRegistrationId: UInt32

        guard let data = messageString.data(using: .utf8) else {
            failure(ERR_NATIVE_FAILED, "String encoding to bytes failed")
            return
        }

        do {
            cipherTextMessage = try sessionCipher.encrypt(data)
            remoteRegistrationId = try sessionCipher.getRemoteRegistrationId();
        } catch {
            failure(ERR_NATIVE_FAILED, "\(error)")
            return
        }

        var message = [String : Any]()
        message["type"] = 1
        message["destination"] = username
        message["content"] = ""
        message["timestamp"] = self.currentTimestamp()
        message["destinationDeviceId"] = 1
        message["destinationRegistrationId"] = remoteRegistrationId
        message["body"] = cipherTextMessage.message.base64EncodedString()


        let params = ["messages" : [message]]

        self.signalServer.call(urlPath: URL_MESSAGES + "/" + username, method: .PUT, parameters: params, success: { (dict) in
            if dict.count != 0 && dict["staleDevices"] != nil {
                // staleDevices found, request new user PreKey and retry message send
                self.requestPreKeys(username: username, success: { _ in
                    self.sendMessage(username: username, messageString: messageString, success: { (message) in
                        success(message)
                    }, failure: { (code, error) in
                        failure(code, "\(error)")
                    })
                }, failure: { (code, error) in
                    failure(code, "\(error)")
                })
            } else {
                let parsedMessage = ParsedMessageDTO()
                parsedMessage.username = self.username
                parsedMessage.device = 1
                parsedMessage.serverTimestamp = self.currentTimestamp() * 1000
                parsedMessage.savedTimestamp = self.currentTimestamp()
                parsedMessage.content = messageString
                MessagesStorage().save(message: parsedMessage, for: username)
                success("ok")
            }
        }) { (error) in
            failure(ERR_SERVER_FAILED, "\(error)")
        }
    }

}

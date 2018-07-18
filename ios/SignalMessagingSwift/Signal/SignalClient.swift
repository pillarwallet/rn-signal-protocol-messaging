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
        
        var parameters = [String : Any]()
        let signalingKey = self.generateRandomBytes()
        
        parameters["signalingKey"] = signalingKey
        parameters["fetchesMessages"] = true
        parameters["registrationId"] = registrationId
        parameters["name"] = self.username
        parameters["voice"] = false
        
        self.signalServer.call(urlPath: URL_ACCOUNTS, method: .PUT, parameters: parameters, success: { (dict) in
            do {
                try self.registerPreKeys(store: store, success: { (response) in
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
    
    private func currentTimestamp() -> Int {
        return Int(Date().timeIntervalSince1970)
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
    
    private func registerPreKeys(store: SignalStore, success: @escaping (_ success: String) -> Void, failure: @escaping (_ error: String, _ message: String) -> Void) throws {
        let identityKeyPair = try Signal.generateIdentityKeyPair()
        ProtocolStorage().storeIdentityKeyPair(keyPair: identityKeyPair)
        
        let preKeys = try Signal.generatePreKeys(start: 1, count: 100)
        let lastPreKeyIndex = UInt32(GL_MEDIUM_INT)
        
        var lastRecordKey: SessionPreKey?
        let lastRecordKeyData = store.preKeyStore.load(preKey: lastPreKeyIndex)
        
        do {
            if (lastRecordKeyData == nil) {
                lastRecordKey = try Signal.generatePreKeys(start: lastPreKeyIndex, count: 1).first
            } else {
                lastRecordKey = try SessionPreKey(from: lastRecordKeyData!)
            }
        } catch {
            print("Last recrod key Error info: \(error)")
        }
        
        // store pre keys
        let parameters = try preKeys.map { (preKey) -> [String : Any] in
            _ = store.preKeyStore.store(preKey: try preKey.data(), for: preKey.id)
            return ["keyId" : preKey.id, "publicKey" : preKey.keyPair.publicKey.base64EncodedString()]
        }
        
        // store signed pre key
        let signedPreKey: SessionSignedPreKey = try Signal.generate(signedPreKey: 1, identity: identityKeyPair, timestamp: 0)
        _ = store.signedPreKeyStore.store(signedPreKey: try signedPreKey.data(), for: signedPreKey.id)
        
        var requestJSON = [String : Any]()
        
        guard let lastResortKey = lastRecordKey else {
            failure(ERR_NATIVE_FAILED, "lastResortKey is nil")
            return
        }
        
        requestJSON["lastResortKey"] = ["keyId" : lastResortKey.id, "publicKey" : lastResortKey.keyPair.publicKey.base64EncodedString()]
        requestJSON["preKeys"] = parameters
        requestJSON["identityKey"] = identityKeyPair.publicKey.base64EncodedString()
        requestJSON["signedPreKey"] = ["keyId" : signedPreKey.id, "publicKey" : signedPreKey.keyPair.publicKey.base64EncodedString(), "signature" : signedPreKey.signature.base64EncodedString()]
        
        self.signalServer.call(urlPath: URL_KEYS, method: .PUT, parameters: requestJSON, success: { (dict) in
            success("ok")
        }) { (error) in
            failure(ERR_SERVER_FAILED, "\(error)")
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
                    }
                    
                    success("ok")
                } else {
                    failure(ERR_SERVER_FAILED, "wrong data received")
                }
            }
        }) { (error) in
            failure(ERR_SERVER_FAILED, "\(error)")
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

        self.signalServer.call(urlPath: URL_GCM, method: .PUT, parameters: ["gcmRegistrationId" : fcmId], success: { (success) in
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
        
        let address = SignalAddress(name: username, deviceId: 1)
        let sessionCipher = SessionCipher(for: address, in: store)
        
        guard store.sessionStore.containsSession(for: address) else {
            print(ERR_NATIVE_FAILED)
            print("no active sessions")
            return [String : Any]()
        }
        
        var parsedMessages = [ParsedMessageDTO]()
        var unread = [String : Any]()
        
        messages.forEach { (messageDict) in
            let message = MessageDTO(dictionary: messageDict)
            let currentCount = unread[message.source] as? Int ?? 0
            unread[message.source] = currentCount+1
            
            if username == message.source,
                decodeAndSave,
                let data = message.messageData() {
                
                let parsedMessage = ParsedMessageDTO()
                parsedMessage.username = username
                parsedMessage.device = 1
                parsedMessage.serverTimestamp = message.timestamp
                parsedMessage.savedTimestamp = self.currentTimestamp()
                
                self.signalServer.call(urlPath: "\(URL_MESSAGES)/\(username)/\(message.timestamp)", method: .DELETE, success: { (response) in }, failure: { (error) in })
                
                var preKeyData: Data? = nil
                do {
                    let cipher = CiphertextMessage(type: .preKey, message: data)
                    preKeyData = try sessionCipher.decrypt(message: cipher)
                } catch {
                    do {
                        let cipher = CiphertextMessage(type: .signal, message: data)
                        preKeyData = try sessionCipher.decrypt(message: cipher)
                    } catch {
                        print(ERR_NATIVE_FAILED)
                        print("Error info: \(error)")
                    }
                }
                
                if preKeyData != nil  {
                    if let receivedMessage = String(data: preKeyData!, encoding: .utf8) {
                        print(receivedMessage)
                        parsedMessage.content = receivedMessage
                    }
                    parsedMessages.append(parsedMessage)
                    MessagesStorage().save(message: parsedMessage, for: username)
                }
            }
        }
        
        MessagesStorage().saveUnreadCount(for: username, count: parsedMessages.count)
        
        var finalDict = [String : Any]()
        finalDict["unreadCount"] = unread
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
        
        guard let data = messageString.data(using: .utf8) else {
            failure(ERR_NATIVE_FAILED, "String encoding to bytes failed")
            return
        }
        
        do {
            cipherTextMessage = try sessionCipher.encrypt(data)
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
        message["destinationRegistrationId"] = ""
        message["body"] = cipherTextMessage.message.base64EncodedString()
        
        print(cipherTextMessage.type.rawValue)
        
        let params = ["messages" : [message]]
        
        self.signalServer.call(urlPath: URL_MESSAGES + "/" + username, method: .PUT, parameters: params, success: { (dict) in
            let parsedMessage = ParsedMessageDTO()
            parsedMessage.username = self.username
            parsedMessage.device = 1
            parsedMessage.serverTimestamp = self.currentTimestamp() * 1000
            parsedMessage.savedTimestamp = self.currentTimestamp()
            parsedMessage.content = messageString
            MessagesStorage().save(message: parsedMessage, for: username)
            success("ok")
        }) { (error) in
            failure(ERR_SERVER_FAILED, "\(error)")
        }
    }
    
}

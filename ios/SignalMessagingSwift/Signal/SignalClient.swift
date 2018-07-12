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
    
    // RCTPromiseRejectBlock)(NSString *code, NSString *message, NSError *error);
    
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
                let identityKey = dict["identityKey"] as? String ?? ""
                let identityData = Data(base64Encoded: identityKey) ?? Data()
                let firstDevice = devices.first as? [String : Any] ?? [String : Any]()
                
                let deviceId = firstDevice["deviceId"] as? Int ?? 1
                let registrationId = firstDevice["registrationId"] as? Int ?? 1
                
                let preKey = firstDevice["preKey"] as? [String : Any] ?? [String : Any]()
                let preKeyId = preKey["keyId"] as? Int ?? 1
                let preKeyDataString = preKey["publicKey"] as? String ?? ""
                let preKeyData = Data(base64Encoded: preKeyDataString) ?? Data()
                
                let signedPreKey = firstDevice["signedPreKey"] as? [String : Any] ?? [String : Any]()
                let signedPreKeyId = signedPreKey["keyId"] as? Int ?? 1
                let signedPreKeyDataString = signedPreKey["publicKey"] as? String ?? ""
                let signedPreKeyData = Data(base64Encoded: signedPreKeyDataString) ?? Data()
                
                let signatureString = signedPreKey["signature"] as? String ?? ""
                let signature = Data(base64Encoded: signatureString) ?? Data()
                
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
            }
        }) { (error) in
            failure(ERR_SERVER_FAILED, "\(error)")
        }
    }
    
    
    func getContactMessages(username: String, decodeAndSave: Bool, success: @escaping (_ success: [String : Any]) -> Void, failure: @escaping (_ error: String, _ message: String) -> Void) {
        self.signalServer.call(urlPath: URL_MESSAGES, method: .GET, success: { (dict) in
            self.parseMessages(username: username, decodeAndSave: true, messagesDictionary: dict)
            success(dict)
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
    
    private func parseMessages(username: String, decodeAndSave: Bool, messagesDictionary: [String : Any]) {
        guard let store = self.store() else {
            print(ERR_NATIVE_FAILED)
            return
        }
        
        guard let messages = messagesDictionary["messages"] as? [[String : Any]] else {
            print(ERR_SERVER_FAILED)
            return
        }
        
        let address = SignalAddress(name: username, deviceId: 1)
        let sessionCipher = SessionCipher(for: address, in: store)
        
        guard store.sessionStore.containsSession(for: address) else {
            print(ERR_NATIVE_FAILED)
            print("no active sessions")
            return
        }
        
        var parsedMessages = [ParsedMessageDTO]()
        
        messages.forEach { (messageDict) in
            let message = MessageDTO(dictionary: messageDict)
            
            if username == message.source,
                decodeAndSave,
                let data = message.messageData() {
                
                let parsedMessage = ParsedMessageDTO()
                parsedMessage.username = username
                parsedMessage.device = 1
                parsedMessage.serverTimestamp = message.timestamp
                parsedMessage.savedTimestamp = self.currentTimestamp()
                
                do {
                    let cipher = CiphertextMessage(from: data)
                    let preKeyData = try sessionCipher.decrypt(message: cipher)
                    
                    if let dict = try JSONSerialization.jsonObject(with: preKeyData, options: JSONSerialization.ReadingOptions.allowFragments) as? [String : Any] {
                        if let body = dict["body"] as? [String : Any] {
                            parsedMessage.content = body["message"] as? String ?? ""
                        }
                    }
                    
                    parsedMessages.append(parsedMessage)
                    MessagesStorage().save(message: parsedMessage, for: username)
                } catch {
                    print(ERR_NATIVE_FAILED)
                    print("Error info: \(error)")
                }
            }
        }
        
        MessagesStorage().saveUnreadCount(for: username, count: parsedMessages.count)
    }
    
    func sendMessage(username: String, messageString: String, success: @escaping (_ success: String) -> Void, failure: @escaping (_ error: String, _ message: String) -> Void) {
        guard let store = self.store() else {
            failure(ERR_NATIVE_FAILED, "Store is invalid")
            return
        }
        
        let address = SignalAddress(name: username, deviceId: 1)
        let sessionCipher = SessionCipher(for: address, in: store)
        let messageBody: [String : Any] = ["type" : "message", "body" : ["message" : messageString]]
        var cipherTextMessage: CiphertextMessage
        
        do {
            let data = try JSONSerialization.data(withJSONObject: messageBody, options: .prettyPrinted)
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
        message["body"] = cipherTextMessage.data.base64EncodedString()
        
        let params = ["messages" : [message]]
        
        self.signalServer.call(urlPath: URL_MESSAGES + "/" + username, method: .PUT, parameters: params, success: { (dict) in
            success("ok")
        }) { (error) in
            failure(ERR_SERVER_FAILED, "\(error)")
        }
    }
    
}










//
//  RNSignalClientModule.swift
//  myAwesomeProject
//
//  Created by Mantas on 07/07/2018.
//  Copyright © 2018 Facebook. All rights reserved.
//

import UIKit
import SignalProtocol
import Sentry

let ERR_WRONG_CONFIG = "ERR_WRONG_CONFIG"
let ERR_SERVER_FAILED = "ERR_SERVER_FAILED"
let ERR_NATIVE_FAILED = "ERR_NATIVE_FAILED"
let ERR_ADD_CONTACT_FAILED = "ERR_ADD_CONTACT_FAILED"

@objc(RNSignalClientModule)
class RNSignalClientModule: NSObject {

    private var signalResetVersion: Int
    private var username: String
    private var host: String
    private var signalClient: SignalClient

    override init() {
        self.signalResetVersion = 0
        self.username = ""
        self.host = ""
        self.signalClient = SignalClient(username: "", accessToken: "", host: "", logger: Logger(isLoggable: false), signalResetVersion: self.signalResetVersion)
        super.init()
    }

    @objc static func requiresMainQueueSetup() -> Bool {
        return true
    }

    @objc func createClient(_ config: NSDictionary, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        self.username = config.object(forKey: "username") as! String
        self.host = config.object(forKey: "host") as! String
        let accessToken = config.object(forKey: "accessToken") as! String
        let isLoggable = config.object(forKey: "isSendingLogs") as! Bool
        let logger = Logger(isLoggable: isLoggable)
        let signalResetVersion = self.signalResetVersion
        let existingSignalResetVersion = ProtocolStorage().getSignalResetVersion()

        // ---
        // check pre key amount in order to reset from a version which was generating large amounts of prekeys
        let preKeys = ProtocolStorage().get(for: .PRE_KEYS_JSON_FILENAME)
        let performSoftReset = preKeys.count > 300
        // ---

        self.signalClient = SignalClient(username: username, accessToken: accessToken, host: host, logger: logger, signalResetVersion: signalResetVersion)
        if isLoggable {
            do {
                let sentryDSN = config.object(forKey: "errorTrackingDSN") as! String
                Client.shared = try Client(dsn: sentryDSN)
                try Client.shared?.startCrashHandler()
            } catch {
                // sentry unavailable
            }
        }

        if !performSoftReset && ProtocolStorage().getLocalUsername() == username && ProtocolStorage().isLocalRegistered() {
            self.signalClient.checkPreKeys()
            resolve("ok")
        } else {
            if (performSoftReset) {
                logger.sendInfoMessage(message: "iOS Signal soft reset version triggered: \(signalResetVersion)" )
                ProtocolStorage().destroyAllExceptMessages()
            } else {
                ProtocolStorage().destroyAll()
            }
            self.signalClient.register(success: { (success) in
                resolve(success)
            }) { (error, message) in
                reject(error, message, nil)
            }
        }
    }

    @objc func registerAccount(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        self.signalClient.register(success: { (success) in
            resolve(success)
        }) { (error, message) in
            reject(error, message, nil)
        }
    }

    @objc func resetAccount(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        ProtocolStorage().destroyAll()
        resolve("ok")
    }

    @objc func addContact(_ config: NSDictionary, forceAdd: Bool, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        let username = (config.object(forKey: "username") as? String) ?? ""
        let address = SignalAddress(name: username, deviceId: 1)
        if let result = self.signalClient.store()?.sessionStore.containsSession(for: address), result == false || forceAdd {
            self.signalClient.requestPreKeys(username: username, success: { (success) in
                resolve(success)
            }) { (error, message) in
                reject(error, message, nil)
            }
        } else {
            resolve("ok")
        }
    }

    @objc func setFcmId(_ fcmId: String, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        self.signalClient.saveFCMId(fcmId: fcmId, success: {
            resolve("ok")
        }) { (error, message) in
            reject(error, message, nil)
        }
    }

    @objc func deleteContact(_ username: String, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        let address = SignalAddress(name: username, deviceId: 1);
        _ = self.signalClient.store()?.sessionStore.deleteSession(for: address)
        ProtocolStorage().removeRemoteIdentity(for: address)
        MessagesStorage().deleteAllContactMessages(for: username)
        self.signalClient.deleteContactPendingMessages(username: username, messageTag: "*", success: { (success) in
            print("ok: pending messages deleted")
        }) { (error, message) in
            print("\(error): \(message)")
        }
        resolve("ok")
    }

    @objc func deleteContactMessages(_ username: String, messageTag: String, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        MessagesStorage().deleteContactMessages(for: username, tag: messageTag)
        self.signalClient.deleteContactPendingMessages(username: username, messageTag: messageTag, success: { (success) in
            resolve(success)
        }) { (error, message) in
            reject(error, message, nil)
        }
    }

    @objc func receiveNewMessagesByContact(_ username: String, messageTag: String, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        self.signalClient.getContactMessages(username: username, messageTag: messageTag, decodeAndSave: true, success: { (success) in
            resolve(success)
        }) { (error, message) in
            reject(error, message, nil)
        }
    }

    @objc func getMessagesByContact(_ username: String, messageTag: String, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        let sortedMessages = MessagesStorage().getMessages(for: username, tag: messageTag).sorted { (messageOne, messageTwo) -> Bool in
            return messageOne.savedTimestamp > messageTwo.savedTimestamp
            }.compactMap { (message) -> [String : Any]? in
                return message.dictionary
        }

        if let data = try? JSONSerialization.data(withJSONObject: sortedMessages, options: .prettyPrinted) {
            let jsonSring = String(data: data, encoding: .utf8)
            resolve(jsonSring)
        }
    }

    @objc func getExistingMessages(_ messageTag: String, resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        if let data = try? JSONSerialization.data(withJSONObject: self.signalClient.getAllContactMessages(messageTag: messageTag), options: .prettyPrinted) {
            let jsonSring = String(data: data, encoding: .utf8)
            resolve(jsonSring)
        }
    }

    @objc func getUnreadMessagesCount(_ messageTag: String, resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        self.signalClient.getContactMessages(username: "", messageTag: messageTag, decodeAndSave: false, success: { (dict) in
            if let data = try? JSONSerialization.data(withJSONObject: dict, options: .prettyPrinted) {
                let jsonSring = String(data: data, encoding: .utf8)
                resolve(jsonSring)
            } else {
                reject("error", "serialization error in getUnreadMessagesCount", nil)
            }
        }) { (error, message) in
            reject(error, message, nil)
        }
    }

    @objc func sendMessageByContact(_ messageTag: String, config: NSDictionary, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        self.signalClient.sendMessage(
            username: config.object(forKey: "username") as! String,
            messageString: config.object(forKey: "message") as! String,
            messageTag: messageTag,
            silent: false,
            success: { (success) in resolve(success) }
        ) { (error, message) in
            reject(error, message, nil)
        }
    }

    @objc func sendSilentMessageByContact(_ messageTag: String, config: NSDictionary, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        self.signalClient.sendMessage(
            username: config.object(forKey: "username") as! String,
            messageString: config.object(forKey: "message") as! String,
            messageTag: messageTag,
            silent: true,
            success: { (success) in resolve(success) }
        ) { (error, message) in
            reject(error, message, nil)
        }
    }

    @objc func prepareApiBody(_ messageTag: String, config: NSDictionary, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        let params = self.signalClient.prepareApiBody(
            username: config.object(forKey: "username") as! String,
            messageString: config.object(forKey: "message") as! String,
            messageTag: messageTag,
            silent: false,
            failure: { (error) in
                reject(ERR_NATIVE_FAILED, "\(error)", nil)
        }
        );

        if (params.isEmpty) {
            return
        }

        if let data = try? JSONSerialization.data(withJSONObject: params, options: .prettyPrinted) {
            let jsonSring = String(data: data, encoding: .utf8)
            resolve(jsonSring)
        }

    }

    @objc func saveSentMessage(_ messageTag: String, config: NSDictionary, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        self.signalClient.saveSentMessage(
            messageTag: messageTag,
            username: config.object(forKey: "username") as! String,
            messageString: config.object(forKey: "message") as! String,
            timestamp: Int64(config.object(forKey: "timestamp") as! NSNumber)
        );
        resolve("ok")
    }

    @objc func decryptReceivedBody(_ receivedBody: String, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        let decryptedBytes = self.signalClient.decryptReceivedBody(body: receivedBody)
        if (decryptedBytes.length == 0) {
            reject(ERR_NATIVE_FAILED, "Failed to decrypt received body", nil)
            return;
        }
        let encoded = decryptedBytes.base64EncodedString(options: NSData.Base64EncodingOptions.endLineWithLineFeed);
        if (encoded.count == 0) {
            reject(ERR_NATIVE_FAILED, "Failed to Base64 encode decrypted body", nil)
            return;
        }
        resolve(encoded)
    }

    @objc func decryptSignalMessage(_ messageTag: String, receivedMessage: String, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        self.signalClient.decryptSignalMessage(
            messageTag: messageTag,
            receivedMessage: receivedMessage,
            success: { (message) in resolve(message) },
            failure: { (error) in
                reject(ERR_NATIVE_FAILED, "\(error)", nil)
        }
        )
    }


    @objc func deleteSignalMessage(_ username: String, timestamp: NSInteger, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        self.signalClient.deleteSignalMessage(
            username: username,
            timestamp: timestamp,
            success: { (message) in resolve(message) }
        )
    }
}

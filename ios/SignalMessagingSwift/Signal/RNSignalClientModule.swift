//
//  RNSignalClientModule.swift
//  myAwesomeProject
//
//  Created by Mantas on 07/07/2018.
//  Copyright © 2018 Facebook. All rights reserved.
//

import UIKit
import SignalProtocol

let ERR_WRONG_CONFIG = "ERR_WRONG_CONFIG"
let ERR_SERVER_FAILED = "ERR_SERVER_FAILED"
let ERR_NATIVE_FAILED = "ERR_NATIVE_FAILED"
let ERR_ADD_CONTACT_FAILED = "ERR_ADD_CONTACT_FAILED"

@objc(RNSignalClientModule)
class RNSignalClientModule: NSObject {
    
    private var username: String
    private var password: String
    private var host: String
    private var signalClient: SignalClient
    
    override init() {
        self.username = ""
        self.password = ""
        self.host = ""
        self.signalClient = SignalClient(username: "", password: "", host: "")
        super.init()
    }
    
    @objc static func requiresMainQueueSetup() -> Bool {
        return true
    }
    
    @objc func createClient(_ username: String, password: String, host: String, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        self.username = username
        self.password = password
        self.host = host
        self.signalClient = SignalClient(username: username, password: password, host: host)
        
        if ProtocolStorage().getLocalUsername() == username && ProtocolStorage().isLocalRegistered() {
            self.signalClient.checkPreKeys()
            resolve("ok")
        } else {
            ProtocolStorage().destroyAll()
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
    
    @objc func addContact(_ username: String, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        let address = SignalAddress(name: username, deviceId: 1)
        if let result = self.signalClient.store()?.sessionStore.containsSession(for: address), result == false {
//            _ = self.signalClient.store()?.sessionStore.deleteSession(for: address)
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
        let result = self.signalClient.store()?.sessionStore.deleteSession(for: SignalAddress(name: username, deviceId: 1))
        resolve("ok")
    }
    
    @objc func receiveNewMessagesByContact(_ username: String, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        self.signalClient.getContactMessages(username: username, decodeAndSave: true, success: { (success) in
            resolve(success)
        }) { (error, message) in
            reject(error, message, nil)
        }
    }
    
    @objc func getChatByContact(_ username: String, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        let sortedMessages = MessagesStorage().getMessages(for: username).sorted { (messageOne, messageTwo) -> Bool in
            return messageOne.savedTimestamp > messageTwo.savedTimestamp
        }.compactMap { (message) -> [String : Any]? in
            return message.dictionary
        }
        
        if let data = try? JSONSerialization.data(withJSONObject: sortedMessages, options: .prettyPrinted) {
            let jsonSring = String(data: data, encoding: .utf8)
            print(jsonSring)
            resolve(jsonSring)
        }
    }
    
    @objc func getExistingChats(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        if let data = try? JSONSerialization.data(withJSONObject: self.signalClient.getAllContactMessages(), options: .prettyPrinted) {
            let jsonSring = String(data: data, encoding: .utf8)
            print(jsonSring)
            resolve(jsonSring)
        }
    }
    
    @objc func getUnreadMessagesCount(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        self.signalClient.getContactMessages(username: "", decodeAndSave: false, success: { (dict) in
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
    
    @objc func sendMessageByContact(_ username: String, messageString: String, resolver resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        self.signalClient.sendMessage(username: username, messageString: messageString, success: { (success) in
            resolve(success)
        }) { (error, message) in
            reject(error, message, nil)
        }
    }
}

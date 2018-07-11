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
    
    @objc func createClient(_ username: String, password: String, host: String) {
        self.username = username
        self.password = password
        self.host = host
        self.signalClient = SignalClient(username: username, password: password, host: host)
        
        //self.test()
    }
    
    func test() {
        self.registerAccount { (response) in
            self.addContact("mantas35", { (response) in
                self.sendMessage("mantas35", message: "hello", { (response) in
                    
                })
                
                self.receiveNewMessageByContact("mantas35", { (response) in
                    
                })
            })
        }
    }
    
    @objc func registerAccount(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        self.signalClient.register(success: { (success) in
            resolve(success)
        }) { (error, message) in
            reject(error, message)
        }
    }
    
    @objc func resetAccount(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        self.signalClient.store()?.identityKeyStore.destroy()
        resolve("ok")
    }
    
    @objc func addContact(_ username: String, _ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        self.signalClient.requestPreKeys(username: username, success: { (success) in
            resolve(success)
        }) { (error, message) in
            reject(error, message)
        }
    }
    
    @objc func deleteContact(_ username: String, _ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        let result = self.signalClient.store()?.sessionStore.deleteSession(for: SignalAddress(name: username, deviceId: 1))
        resolve("ok")
    }
    
    @objc func receiveNewMessageByContact(_ username: String, _ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        self.signalClient.getContactMessages(username: username, decodeAndSave: true, success: { (success) in
            resolve(success)
        }) { (error, message) in
            reject(error, message)
        }
    }
    
    @objc func getReceivedMessagesByContact(_ username: String, _ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        let messages = MessagesStorage().getMessages(for: username).compactMap { (message) -> [String : Any]? in
            return message.dictionary
        }
        
        resolve(messages)
    }
    
    @objc func getUnreadMessagesCount(_ username: String, _ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        let count = MessagesStorage().getUnreadCount(for: username)
        resolve(count)
    }
    
    @objc func sendMessage(_ receiver: String, message: String, _ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        self.signalClient.sendMessage(username: username, messageString: message, success: { (success) in
            resolve(success)
        }) { (error, message) in
            reject(error, message)
        }
    }
}

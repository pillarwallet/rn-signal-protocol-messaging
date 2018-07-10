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
  
  @objc func registerAccount(_ callback: @escaping RCTResponseSenderBlock) {
    self.signalClient.register { (success) in
      callback([["success" : success]])
    }
  }
  
  @objc func resetAccount(_ callback: @escaping RCTResponseSenderBlock) {
    self.signalClient.store()?.identityKeyStore.destroy()
    callback([["success" : true]])
  }
  
  @objc func addContact(_ username: String, _ callback: @escaping RCTResponseSenderBlock) {
    self.signalClient.requestPreKeys(username: username) { (success) in
      callback([["success" : success]])
    }
  }
  
  @objc func deleteContact(_ username: String, _ callback: @escaping RCTResponseSenderBlock) {
    let result = self.signalClient.store()?.sessionStore.deleteSession(for: SignalAddress(name: username, deviceId: 1))
    callback([["success" : result]])
  }
  
  @objc func receiveNewMessageByContact(_ username: String, _ callback: @escaping RCTResponseSenderBlock) {
    self.signalClient.getContactMessages(username: username, decodeAndSave: true) { (success) in
      callback([["success" : success]])
    }
  }
  
  @objc func getReceivedMessagesByContact(_ username: String, _ callback: @escaping RCTResponseSenderBlock) {
    let messages = MessagesStorage().getMessages(for: username).compactMap { (message) -> [String : Any]? in
      return message.dictionary
    }
    callback([messages])
  }
  
  @objc func getUnreadMessagesCount(_ username: String, _ callback: @escaping RCTResponseSenderBlock) {
    let count = MessagesStorage().getUnreadCount(for: username)
    callback([count])
  }
  
  @objc func sendMessage(_ receiver: String, message: String, _ callback: @escaping RCTResponseSenderBlock) {
    self.signalClient.sendMessage(username: receiver, messageString: message) { (success) in
      callback([["success" : success]])
    }
  }
}

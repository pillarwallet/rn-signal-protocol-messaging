//
//  MessagesStorage.swift
//  myAwesomeProject
//
//  Created by Mantas on 08/07/2018.
//  Copyright © 2018 Facebook. All rights reserved.
//

import UIKit

class MessagesStorage: ProtocolStorage {
    
    
  func getMessageStorageFilename(for tag: String) -> DownloadsType {
    switch tag {
        case "chat": return .MESSAGES_CHAT_JSON_FILENAME
        case "tx-note": return .MESSAGES_TXNOTE_JSON_FILENAME
        default: return .MESSAGES_OTHER_JSON_FILENAME
    }
  }
  
  func save(message: ParsedMessageDTO, for username: String, tag: String) {
    var localJson = self.getAllMessages(for: tag)
    var userData = [String : Any]()
    if let userDataFile = localJson[username] as? [String : Any] {
      userData = userDataFile
    }
    
    var parsedMessages = [[String : Any]]()
    if let userMessagesFile = userData["messages"] as? [[String : Any]] {
      parsedMessages = userMessagesFile
    }
    
    parsedMessages.append(message.dictionary)
    userData["messages"] = parsedMessages
    localJson[username] = userData
    self.save(array: ["allMessages" : localJson], type: getMessageStorageFilename(for: tag))
  }
  
  func getMessages(for username: String, tag: String) -> [ParsedMessageDTO] {
        let localJson = self.getAllMessages(for: tag)
    guard let userDict = localJson[username] as? [String : Any], let userMessagesDict = userDict["messages"] as? [[String : Any]] else {
      return [ParsedMessageDTO]()
    }
    
    let userMessages = userMessagesDict.compactMap { (dict) -> ParsedMessageDTO? in
      return ParsedMessageDTO(dictionary: dict)
    }
    
    return userMessages
  }
  
  func getUnreadCount(for username: String, tag: String) -> Int {
    let localJson = self.getAllMessages(for: tag)
    guard let userDict = localJson[username] as? [String : Any], let unreadCount = userDict["unreadCount"] as? Int else {
      return 0
    }
    
    return unreadCount
  }
  
  func saveUnreadCount(for username: String, count: Int, tag: String) {
    let currentCount = self.getUnreadCount(for: username, tag: tag)
    var localJson = self.getAllMessages(for: tag)
    var userData = [String : Any]()
    if let userDataFile = localJson[username] as? [String : Any] {
      userData = userDataFile
    }
    
    userData["unreadCount"] = count + currentCount
    localJson[username] = userData
    self.save(array: ["allMessages" : localJson], type: getMessageStorageFilename(for: tag))
  }
  
  func getAllMessages(for tag: String) -> [String : Any] {
    let localJson = self.get(for: getMessageStorageFilename(for: tag))
    guard let list = localJson["allMessages"] as? [String : Any] else {
      return [String : Any]()
    }
    
    return list
  }
  
  func deleteContactMessages(for username: String, tag: String) {
    var localJson = self.getAllMessages(for: tag)
    localJson[username] = nil
    self.save(array: ["allMessages" : localJson], type: getMessageStorageFilename(for: tag))
  }

}


/*
 
 {
   "allMessages" : {
     "user1" : {
        "unreadCount" : 0,
        "messages" : [
           
 
        ]
      }
    }
 }
 
 */

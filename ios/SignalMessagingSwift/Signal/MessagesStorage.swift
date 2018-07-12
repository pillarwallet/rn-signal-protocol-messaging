//
//  MessagesStorage.swift
//  myAwesomeProject
//
//  Created by Mantas on 08/07/2018.
//  Copyright © 2018 Facebook. All rights reserved.
//

import UIKit

class MessagesStorage: ProtocolStorage {
  
  func save(message: ParsedMessageDTO, for username: String) {
    var localJson = self.getAllMessages()
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
    self.save(array: ["allMessages" : localJson], type: .MESSAGES_JSON_FILENAME)
  }
  
  func getMessages(for username: String) -> [ParsedMessageDTO] {
    let localJson = self.getAllMessages()
    guard let userDict = localJson[username] as? [String : Any], let userMessagesDict = userDict["messages"] as? [[String : Any]] else {
      return [ParsedMessageDTO]()
    }
    
    let userMessages = userMessagesDict.compactMap { (dict) -> ParsedMessageDTO? in
      return ParsedMessageDTO(dictionary: dict)
    }
    
    return userMessages
  }
  
  func getUnreadCount(for username: String) -> Int {
    let localJson = self.getAllMessages()
    guard let userDict = localJson[username] as? [String : Any], let unreadCount = userDict["unreadCount"] as? Int else {
      return 0
    }
    
    return unreadCount
  }
  
  func saveUnreadCount(for username: String, count : Int) {
    let currentCount = self.getUnreadCount(for: username)
    var localJson = self.getAllMessages()
    var userData = [String : Any]()
    if let userDataFile = localJson[username] as? [String : Any] {
      userData = userDataFile
    }
    
    userData["unreadCount"] = count + currentCount
    localJson[username] = userData
    self.save(array: ["allMessages" : localJson], type: .MESSAGES_JSON_FILENAME)
  }
  
  func getAllMessages() -> [String : Any] {
    let localJson = self.get(for: .MESSAGES_JSON_FILENAME)
    guard let list = localJson["allMessages"] as? [String : Any] else {
      return [String : Any]()
    }
    
    return list
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

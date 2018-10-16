//
//  ParsedMessageDTO.swift
//  myAwesomeProject
//
//  Created by Mantas on 08/07/2018.
//  Copyright © 2018 Facebook. All rights reserved.
//

import UIKit

class ParsedMessageDTO: NSObject {
  
  var dictionary = [String : Any]()
  
  init(dictionary: [String : Any]) {
    super.init()
    
    self.dictionary = dictionary
  }
  
  override init() {
    super.init()
  }
  
  var content: String {
    get {
      return self.dictionary["content"] as? String ?? ""
    }
    
    set {
      self.dictionary["content"] = newValue
    }
  }
  
  var username: String {
    get {
      return self.dictionary["username"] as? String ?? ""
    }
    
    set {
      self.dictionary["username"] = newValue
    }
  }

  var type: String {
    get {
        return self.dictionary["type"] as? String ?? "message"
    }

    set {
        self.dictionary["type"] = newValue
    }
  }

  var status: String {
    get {
        return self.dictionary["status"] as? String ?? ""
    }

    set {
        self.dictionary["status"] = newValue
    }
  }

  var device: Int {
    get {
      return self.dictionary["device"] as? Int ?? 1
    }
    
    set {
      self.dictionary["device"] = newValue
    }
  }
  
  var serverTimestamp: Int {
    get {
      return self.dictionary["serverTimestamp"] as? Int ?? 1
    }
    
    set {
      self.dictionary["serverTimestamp"] = newValue
    }
  }
  
  var savedTimestamp: Int {
    get {
      return self.dictionary["savedTimestamp"] as? Int ?? 1
    }
    
    set {
      self.dictionary["savedTimestamp"] = newValue
    }
  }

}

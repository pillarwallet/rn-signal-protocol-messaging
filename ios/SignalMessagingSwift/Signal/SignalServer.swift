//
//  SignalServer.swift
//  PillarWalletSigal
//
//  Created by Mantas on 05/07/2018.
//  Copyright © 2018 Pillar. All rights reserved.
//

import UIKit

//let BASE_URL = "https://pillar-chat-service.herokuapp.com"
let URL_ACCOUNTS = "/v1/accounts"
let URL_KEYS = "/v2/keys"
let URL_MESSAGES = "/v1/messages"
let URL_GCM = "/v1/accounts/gcm"


enum HTTPMethod: String {
  case PUT = "PUT"
  case GET = "GET"
  case POST = "POST"
  case DELETE = "DELETE"
}

class SignalServer: NSObject {
  
  private let signalUsername: String
  private let signalPassword: String
  private let host: String
  
  init(username: String, password: String, host: String) {
    self.signalUsername = username
    self.signalPassword = password
    self.host = host
    super.init()
  }
  
  private func getFullApiUrl(target_url: String) -> String {
    return self.host + target_url
  }
  
  func call(urlPath: String, method: HTTPMethod, parameters: [String : Any] = [String : Any](), success: @escaping(_ dictionary: [String : Any]) -> Void, failure: @escaping (_ error: Error?) -> Void) {
    guard let url = URL(string: self.getFullApiUrl(target_url: urlPath)) else {
      return
    }
    
    var request = URLRequest(url: url)
    request.httpMethod = method.rawValue
    if (method != .DELETE) {
        request.setValue("\(Int(Date().timeIntervalSince1970))", forHTTPHeaderField: "Token-Timestamp")
        request.setValue("address", forHTTPHeaderField: "Token-ID-Address")
        request.setValue("signature", forHTTPHeaderField: "Token-Signature")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
    }
    
    let loginString = String(format: "%@:%@", self.signalUsername, self.signalPassword)
    let loginData = loginString.data(using: String.Encoding.utf8)!
    let base64LoginString = loginData.base64EncodedString()
    request.setValue("Basic \(base64LoginString)", forHTTPHeaderField: "Authorization")
    
    if (parameters.keys.count > 0) {
      do {
        request.httpBody = try JSONSerialization.data(withJSONObject: parameters, options: .prettyPrinted)
      } catch {
        
      }
    }
    
    URLSession.shared.dataTask(with: request) { (data, response, error) in
      DispatchQueue.main.async {
        guard error == nil else {
          failure(error)
          return
        }
        
        do {
          let decoded = try JSONSerialization.jsonObject(with: data ?? Data(), options: .mutableContainers)
          if let dictFromJSON = decoded as? [String: Any] {
            print("Server response")
            print(dictFromJSON)
            success(dictFromJSON)
          }
        } catch {
          print(error.localizedDescription)
        }
        
        if let resp = response as? HTTPURLResponse {
          print("Status Code \(resp.statusCode)")
          
          if resp.statusCode == 204 {
            success([String: Any]())
          }
        }
      }
      
      }.resume()
  }
  
}

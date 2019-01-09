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

enum RuntimeError: Error {
  case runtimeError(String)
}

class SignalServer: NSObject {
  
  private let accessToken: String
  private let host: String
  
  init(accessToken: String, host: String) {
    self.accessToken = accessToken
    self.host = host
    super.init()
  }
  
  private func getFullApiUrl(target_url: String) -> String {
    return self.host + target_url
  }
  
  func call(urlPath: String, method: HTTPMethod, parameters: [String : Any] = [String : Any](), success: @escaping(_ dictionary: [String : Any]) -> Void, failure: @escaping (_ error: Error?) -> Void) {
    guard let url = URL(string: self.getFullApiUrl(target_url: urlPath)) else {
      failure(RuntimeError.runtimeError("URL is incorrect"))
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
    
    request.setValue("Bearer \(self.accessToken)", forHTTPHeaderField: "Authorization")
    
    if (parameters.keys.count > 0) {
      do {
        request.httpBody = try JSONSerialization.data(withJSONObject: parameters, options: .prettyPrinted)
      } catch {
        print("JSONSerialization.data: \(error.localizedDescription)")
        print(error)
        failure(error)
        return
      }
    }
    
    URLSession.shared.dataTask(with: request) { (data, response, error) in
      DispatchQueue.main.async {
        guard error == nil else {
          print(error as Any)
          failure(error)
          return
        }

        if let resp = response as? HTTPURLResponse {
          print("Response Status Code \(resp.statusCode)")
          if resp.statusCode == 204 {
            success([String: Any]())
            return
          }
          if resp.statusCode == 404 {
            failure(NSError(domain: "", code: 404))
            return
          }
        }

        do {
          let decoded = try JSONSerialization.jsonObject(with: data ?? Data(), options: .mutableContainers)
          if let dictFromJSON = decoded as? [String: Any] {
            print("Got server response")
            success(dictFromJSON)
            return
          }
        } catch {
          print("Decoding error: \(error.localizedDescription)");
          print(error);
          failure(error)
          return
        }
      }
    }.resume()
  }

}

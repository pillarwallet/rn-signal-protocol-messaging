//
//  SessionStorePillar.swift
//  PillarWalletSigal
//
//  Created by Mantas on 04/07/2018.
//  Copyright © 2018 Pillar. All rights reserved.
//

import UIKit
import SignalProtocol

class SessionStorePillar: SessionStore {
    
    func storage() -> ProtocolStorage {
        return ProtocolStorage()
    }
    
    func loadSession(for address: SignalAddress) -> (session: Data, userRecord: Data?)? {
        let sessions = self.storage().get(for: .SESSIONS_JSON_FILENAME)
        if let data = sessions[address.name] as? [String : Any] {
            guard let sessionString = data["session"] as? String else {
                return nil
            }
            
            guard let session = Data(base64Encoded: sessionString) else {
                return nil
            }
            
            let userRecordString = data["userRecord"] as? String
            var userRecord: Data?
            if userRecordString != nil {
                userRecord = Data(base64Encoded: userRecordString!)
            }
            
            return (session: session, userRecord: userRecord)
        }
        
        return nil
    }
    
    func subDeviceSessions(for name: String) -> [Int32]? {
        return nil
    }
    
    func store(session: Data, for address: SignalAddress, userRecord: Data?) -> Bool {
        var sessions = self.storage().get(for: .SESSIONS_JSON_FILENAME)
        sessions[address.name] = ["session" : session.base64EncodedString(), "userRecord" : userRecord?.base64EncodedString()]
        self.storage().save(array: sessions, type: .SESSIONS_JSON_FILENAME)
        return true
    }
    
    func containsSession(for address: SignalAddress) -> Bool {
        let sessions = self.storage().get(for: .SESSIONS_JSON_FILENAME)
        return sessions[address.name] != nil
    }
    
    func deleteSession(for address: SignalAddress) -> Bool? {
        var sessions = self.storage().get(for: .SESSIONS_JSON_FILENAME)
        sessions[address.name] = nil
        self.storage().save(array: sessions, type: .SESSIONS_JSON_FILENAME)
        return true
    }
    
    func deleteAllSessions(for name: String) -> Int? {
        let number = self.storage().get(for: .SESSIONS_JSON_FILENAME).count
        self.storage().save(array: [String : Any](), type: .SESSIONS_JSON_FILENAME)
        return number
    }
}

//
//  PreKeyStorePillar.swift
//  PillarWalletSigal
//
//  Created by Mantas on 04/07/2018.
//  Copyright © 2018 Pillar. All rights reserved.
//

import UIKit
import SignalProtocol

class PreKeyStorePillar: PreKeyStore {
    
    func storage() -> ProtocolStorage {
        return ProtocolStorage()
    }
    
    func load(preKey: UInt32) -> Data? {
        let identities = self.storage().get(for: .PRE_KEYS_JSON_FILENAME)
        guard let baseString = identities["\(preKey)"] as? String else {
            return nil
        }
        
        return Data(base64Encoded: baseString)
    }
    
    func store(preKey: Data, for id: UInt32) -> Bool {
        var identities = self.storage().get(for: .PRE_KEYS_JSON_FILENAME)
        identities["\(id)"] = preKey.base64EncodedString()
        self.storage().save(array: identities, type: .PRE_KEYS_JSON_FILENAME)
        
        return true
    }
    
    func contains(preKey: UInt32) -> Bool {
        let identities = self.storage().get(for: .PRE_KEYS_JSON_FILENAME)
        return identities["\(preKey)"] != nil
    }
    
    func remove(preKey: UInt32) -> Bool {
        var identities = self.storage().get(for: .PRE_KEYS_JSON_FILENAME)
        identities["\(preKey)"] = nil
        self.storage().save(array: identities, type: .PRE_KEYS_JSON_FILENAME)
        return true
    }
    
    public func getLastPreKeyIndex() -> UInt32 {
        let identities = self.storage().get(for: .PRE_KEYS_JSON_FILENAME)
        do {
            return UInt32(identities.count)
        } catch {
            return 0
        }
    }

}

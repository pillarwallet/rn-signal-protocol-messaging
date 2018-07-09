//
//  SignedPreKeyStorePillar.swift
//  PillarWalletSigal
//
//  Created by Mantas on 04/07/2018.
//  Copyright © 2018 Pillar. All rights reserved.
//

import UIKit
import SignalProtocol

class SignedPreKeyStorePillar: SignedPreKeyStore {
    
    func storage() -> ProtocolStorage {
        return ProtocolStorage()
    }
    
    func load(signedPreKey: UInt32) -> Data? {
        let identities = self.storage().get(for: .SIGNED_PRE_KEYS_JSON_FILENAME)
        guard let baseString = identities["\(signedPreKey)"] as? String else {
            return nil
        }
        
        return Data(base64Encoded: baseString)
    }
    
    func store(signedPreKey: Data, for id: UInt32) -> Bool {
        var identities = self.storage().get(for: .SIGNED_PRE_KEYS_JSON_FILENAME)
        identities["\(id)"] = signedPreKey.base64EncodedString()
        self.storage().save(array: identities, type: .SIGNED_PRE_KEYS_JSON_FILENAME)
        
        return true
    }
    
    func contains(signedPreKey: UInt32) -> Bool {
        let identities = self.storage().get(for: .SIGNED_PRE_KEYS_JSON_FILENAME)
        return identities["\(signedPreKey)"] != nil
    }
    
    func remove(signedPreKey: UInt32) -> Bool {
        var identities = self.storage().get(for: .SIGNED_PRE_KEYS_JSON_FILENAME)
        identities["\(signedPreKey)"] = nil
        self.storage().save(array: identities, type: .SIGNED_PRE_KEYS_JSON_FILENAME)
        return true
    }
}

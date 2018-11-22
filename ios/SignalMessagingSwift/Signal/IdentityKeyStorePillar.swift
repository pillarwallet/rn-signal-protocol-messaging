//
//  IdentityKeyStorePillar.swift
//  PillarWalletSigal
//
//  Created by Mantas on 04/07/2018.
//  Copyright © 2018 Pillar. All rights reserved.
//

import UIKit
import SignalProtocol

class IdentityKeyStorePillar: IdentityKeyStore {
    
    func storage() -> ProtocolStorage {
        return ProtocolStorage()
    }
    
    func identityKeyPair() -> KeyPair? {
        do {
            return try self.storage().getIdentityKeyPair()
        } catch {
            print("Error info: \(error)")
        }
        
        return nil
    }
    
    func localRegistrationId() -> UInt32? {
        return self.storage().getLocalRegistrationId()
    }
    
    func save(identity: Data?, for address: SignalAddress) -> Bool {
        guard let data = identity else {
            return false
        }
        
        var identities = self.storage().get(for: .IDENTITES_JSON_FILENAME)
        identities[address.name] = data.base64EncodedString()
        self.storage().save(array: identities, type: .IDENTITES_JSON_FILENAME)
        return true
    }
    
    func isTrusted(identity: Data, for address: SignalAddress) -> Bool? {
        let identities = self.storage().get(for: .IDENTITES_JSON_FILENAME)
        guard let baseString = identities[address.name] as? String else {
            return true // trust on first use
        }
        
        return baseString == identity.base64EncodedString()
    }
    
    func destroy() {
        self.storage().destroyAll()
    }

}

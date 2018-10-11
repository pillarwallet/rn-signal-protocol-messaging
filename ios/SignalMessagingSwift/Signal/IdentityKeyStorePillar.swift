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
    
    /*
         This method returns force true as it didn't affect basic flow of Signal.
     
         On 2018-10-10 discovered (on iOS) that this method checks PreKey identity while
         renewing PreKey over existing addresses in device (stale device flow).
     
         TODO: Get into more details at Signal structure level how this method works and prevent force true return.
     */
    func isTrusted(identity: Data, for address: SignalAddress) -> Bool? {
        return true
//        let identities = self.storage().get(for: .IDENTITES_JSON_FILENAME)
//        guard let baseString = identities[address.name] as? String else {
//            return true
//        }
//
//        return baseString == identity.base64EncodedString()
    }
    
    func destroy() {
        self.storage().destroyAll()
    }

}

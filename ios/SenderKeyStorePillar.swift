//
//  SenderKeyStorePillar.swift
//  PillarWalletSigal
//
//  Created by Mantas on 04/07/2018.
//  Copyright © 2018 Pillar. All rights reserved.
//

import UIKit
import SignalProtocol

class SenderKeyStorePillar: SenderKeyStore { // for group messaging
    
    func storage() -> ProtocolStorage {
        return ProtocolStorage()
    }
    
    func store(senderKey: Data, for address: SignalSenderKeyName, userRecord: Data?) -> Bool {
        return false
    }
    
    func loadSenderKey(for address: SignalSenderKeyName) -> (senderKey: Data, userRecord: Data?)? {
        return nil
    }

}

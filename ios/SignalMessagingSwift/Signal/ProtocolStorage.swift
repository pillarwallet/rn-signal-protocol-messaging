//
//  ProtocolStorage.swift
//  PillarWalletSigal
//
//  Created by Mantas on 01/07/2018.
//  Copyright © 2018 Pillar. All rights reserved.
//

import UIKit
import SignalProtocol

enum DownloadsType: String {
  case PRE_KEYS_JSON_FILENAME = "prekeys"
  case SIGNED_PRE_KEYS_JSON_FILENAME = "signed_prekeys"
  case SESSIONS_JSON_FILENAME = "sessions"
  case IDENTITES_JSON_FILENAME = "identites"
  case LOCAL_JSON_FILENAME = "user"
  case MESSAGES_CHAT_JSON_FILENAME = "messages"
  case MESSAGES_TXNOTE_JSON_FILENAME = "messages_txnote"
  case MESSAGES_OTHER_JSON_FILENAME = "messages_other"
}

class ProtocolStorage: NSObject {

    func getLocalRegistrationId() -> UInt32? {
        let localJson = self.get(for: .LOCAL_JSON_FILENAME)
        return localJson["registrationId"] as? UInt32
    }

    func storeLocalRegistrationId(registrationId: UInt32) {
        var localJson = self.get(for: .LOCAL_JSON_FILENAME)
        localJson["registrationId"] = registrationId
        self.save(array: localJson, type: .LOCAL_JSON_FILENAME)
    }

    func storeIdentityKeyPair(keyPair: KeyPair) {
        var localJson = self.get(for: .LOCAL_JSON_FILENAME)
        localJson["identityKeyPairPrivateKey"] = keyPair.privateKey.base64EncodedString()
        localJson["identityKeyPairPublicKey"] = keyPair.publicKey.base64EncodedString()
        self.save(array: localJson, type: .LOCAL_JSON_FILENAME)
    }

    func getIdentityKeyPair() throws -> KeyPair? {
        let localJson = self.get(for: .LOCAL_JSON_FILENAME)
        if let privateKeyString = localJson["identityKeyPairPrivateKey"] as? String,
            let publicKeyString = localJson["identityKeyPairPublicKey"] as? String,
            let privateKey = Data(base64Encoded: privateKeyString),
            let publicKey = Data(base64Encoded: publicKeyString) {
            return KeyPair(publicKey: publicKey, privateKey: privateKey)
        }

        return nil
    }

    func storeLocalUsername(username: String) {
        var localJson = self.get(for: .LOCAL_JSON_FILENAME)
        localJson["username"] = username
        self.save(array: localJson, type: .LOCAL_JSON_FILENAME)
    }

    func getLocalUsername() -> String {
        let localJson = self.get(for: .LOCAL_JSON_FILENAME)
        return localJson["username"] as? String ?? ""
    }

    func isLocalRegistered() -> Bool {
        let localJson = self.get(for: .LOCAL_JSON_FILENAME)
        if localJson["identityKeyPairPublicKey"] != nil && localJson["registrationId"] != nil {
    	        return true;
        }
        return false;
    }

    func destroyAll() {
        let manager = FileManager.default
        let url = manager.urls(for: .documentDirectory, in: .userDomainMask).first! as NSURL
        let path = url.appendingPathComponent("signal")!

        do {
            try manager.removeItem(atPath: path.path)
        } catch {
            print(error)
        }
    }

    // MARK: - Helpers

    func save(array: [String : Any], type: DownloadsType) {
        NSKeyedArchiver.archiveRootObject(array, toFile: self.filePath(for: type))
    }

    func get(for type: DownloadsType) -> [String : Any] {
        if let results = NSKeyedUnarchiver.unarchiveObject(withFile: self.filePath(for: type)) as? [String : Any] {
            return results
        }

        return [String : Any]()
    }

    func filePath(for type: DownloadsType) -> String {
        let manager = FileManager.default

        let url = manager.urls(for: .documentDirectory, in: .userDomainMask).first! as NSURL
        let path = url.appendingPathComponent("signal")!

        do {
            try manager.createDirectory(atPath: path.path, withIntermediateDirectories: true)
        } catch {
            print(error)
        }

        return path.appendingPathComponent(type.rawValue).path
    }

}

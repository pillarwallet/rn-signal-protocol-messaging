//
//  SessionCipher.swift
//  libsignal-protocol-swift
//
//  Created by User on 16.02.18.
//  Copyright © 2018 User. All rights reserved.
//

import Foundation
import SignalModule


/**
 The main entry point for Signal Protocol encrypt/decrypt operations.

 Once a session has been established with `SessionBuilder`,
 this class can be used for all encrypt/decrypt operations within
 that session.
 */
public final class SessionCipher {

    /// The address of the remote party
    let remoteAddress: SignalAddress

    /// The store for persistent storage of keys and sessions
    let store: SignalStore

    /**
     Construct a session cipher for encrypt/decrypt operations on a session.
     In order to use session_cipher, a session must have already been created
     and stored using session_builder.
     - parameter remoteAddress: The remote address that messages will be encrypted to or decrypted from
     - parameter store: The store for the keys and state information
     */
    public init(for remoteAddress: SignalAddress, in store: SignalStore) {
        self.remoteAddress = remoteAddress
        self.store = store
    }

    /// Create a `session_cipher` pointer (needs to be freed)
    private func cipher() throws -> OpaquePointer {
        var cipher: OpaquePointer? = nil
        let result = withUnsafeMutablePointer(to: &cipher) {
            session_cipher_create($0, store.storeContext, remoteAddress.signalAddress, Signal.context)
        }

        guard result == 0 else { throw SignalError(value: result) }
        return cipher!
    }

    /**
     Encrypt a message.
     - parameter message: The plaintext message bytes, optionally padded to a constant multiple.
     - returns: The encrypted message
     - throws: Errors of type `SignalError`
     */
    public func encrypt(_ message: Data) throws -> CiphertextMessage {
        let cipher = try self.cipher()
        defer { session_cipher_free(cipher) }

        var ciphertext: OpaquePointer? = nil

        let result = message.withUnsafeBytes { mPtr in
            withUnsafeMutablePointer(to: &ciphertext) { cPtr in
                session_cipher_encrypt(cipher, mPtr, message.count, cPtr)
            }
        }
        guard result == SG_SUCCESS else { throw SignalError(value: result) }
        defer { signal_type_unref(ciphertext) }

        return CiphertextMessage(pointer: ciphertext!)
    }

    /**
     Decrypt a message from a serialized ciphertext message.
     - note: Possible errors are:
     - `invalidArgument` if the ciphertext message type is not `preKey` or `signal`
     - `unknownError`: The cipher could not be created, or other error
     - `invalidMessage`: The input is not valid ciphertext
     - `duplicateMessage`: The input is a message that has already been received
     - `legacyMessage`: The input is a message formatted by a protocol version that is no longer supported
     - `invalidKeyId`: There is no local pre key record that corresponds to the pre key Id in the message
     - `invalidKey`: The message is formatted incorrectly
     - `untrustedIdentity`: The identity key of the sender is untrusted
     - `noSession`: There is no established session for this contact
     - parameter message: The data of the message to decrypt, either pre key message or signal message
     - returns: The decrypted data
     - throws: Errors of type `SignalError`
     */
    public func decrypt(data: Data) throws -> Data {
        let ciphertext = CiphertextMessage(from: data)
        return try decrypt(message: ciphertext)
    }

    /**
     Decrypt a message from a ciphertext message.
     - note: Possible errors are:
     - `invalidArgument` if the ciphertext message type is not `preKey` or `signal`
     - `unknownError`: The cipher could not be created, or other error
     - `invalidMessage`: The input is not valid ciphertext
     - `duplicateMessage`: The input is a message that has already been received
     - `legacyMessage`: The input is a message formatted by a protocol version that is no longer supported
     - `invalidKeyId`: There is no local pre key record that corresponds to the pre key Id in the message
     - `invalidKey`: The message is formatted incorrectly
     - `untrustedIdentity`: The identity key of the sender is untrusted
     - `noSession`: There is no established session for this contact
     - parameter message: The message to decrypt, either pre key message or signal message
     - returns: The decrypted data
     - throws: Errors of type `SignalError`
     */
    public func decrypt(message: CiphertextMessage) throws -> Data {
        switch message.type {
        case .preKey: return try decrypt(preKeySignalMessage: message.message)
        case .signal: return try decrypt(signalMessage: message.message)
        default: throw SignalError.invalidArgument
        }
    }

    /**
     Decrypt a message.
     - note: Possible errors are:
     - `unknownError`: The cipher could not be created, or other error
     - `invalidMessage`: The input is not valid ciphertext
     - `duplicateMessage`: The input is a message that has already been received
     - `legacyMessage`: The input is a message formatted by a protocol version that is no longer supported
     - `invalidKeyId`: There is no local pre key record that corresponds to the pre key Id in the message
     - `invalidKey`: The message is formatted incorrectly
     - `untrustedIdentity`: The identity key of the sender is untrusted
     - parameter message: The pre key signal message to decrypt.
     - returns: The decrypted data
     - throws: Errors of type `SignalError`
     */
    public func decrypt(preKeySignalMessage message: Data) throws -> Data {
        let cipher = try self.cipher()
        defer { session_cipher_free(cipher) }

        var messagePtr: OpaquePointer? = nil
        var result = message.withUnsafeBytes { mPtr in
            withUnsafeMutablePointer(to: &messagePtr) {
                pre_key_signal_message_deserialize($0, mPtr, message.count, Signal.context)
            }
        }
        guard result == 0 else { throw SignalError(value: result) }
        defer { pre_key_signal_message_destroy(messagePtr) }

        var plaintextPtr: OpaquePointer? = nil
        result = withUnsafeMutablePointer(to: &plaintextPtr) {
            session_cipher_decrypt_pre_key_signal_message(cipher, messagePtr, nil, $0)
        }
        guard result == SG_SUCCESS else { throw SignalError(value: result) }
        defer { signal_buffer_free(plaintextPtr) }

        return Data(signalBuffer: plaintextPtr!)
    }

    /**
     Decrypt a message.
     - note: Possible errors are:
     - `unknownError`: The cipher could not be created, or other error
     - `invalidMessage`: The input is not valid ciphertext
     - `duplicateMessage`: The input is a message that has already been received
     - `legacyMessage`: The input is a message formatted by a protocol version that is no longer supported
     - `noSession`: There is no established session for this contact
     - parameter message: The signal message to decrypt.
     - returns: The result of the operation, and the decrypted data on sucess.
     */
    public func decrypt(signalMessage message: Data) throws -> Data {
        let cipher = try self.cipher()
        defer { session_cipher_free(cipher) }

        var messagePtr: OpaquePointer? = nil
        var result = message.withUnsafeBytes { mPtr in
            withUnsafeMutablePointer(to: &messagePtr) {
                signal_message_deserialize($0, mPtr, message.count, Signal.context)
            }
        }
        guard result == 0 else { throw SignalError(value: result) }
        defer { signal_message_destroy(messagePtr) }

        var plaintextPtr: OpaquePointer? = nil
        result = withUnsafeMutablePointer(to: &plaintextPtr) {
            session_cipher_decrypt_signal_message(cipher, messagePtr, nil, $0)
        }
        guard result == SG_SUCCESS else { throw SignalError(value: result) }
        defer { signal_buffer_free(plaintextPtr) }

        return Data(signalBuffer: plaintextPtr!)
    }
    
    /**
     Get remote user Registration ID.
     - returns: The result of the operation, and the remote user Registration ID data on sucess.
     */
    public func getRemoteRegistrationId() throws -> UInt32 {
        let cipher = try self.cipher()
        
        var remoteRegId: UInt32 = 0
        
        let result = session_cipher_get_remote_registration_id(cipher, &remoteRegId);
        guard result == SG_SUCCESS else { throw SignalError(value: result) }
        
        return remoteRegId
    }
}

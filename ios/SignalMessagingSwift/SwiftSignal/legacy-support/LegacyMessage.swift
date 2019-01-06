import Foundation

public struct LegacyMessage {
    public var serialized: NSData
    
    public init(body: String, signalingKey: String) {
        if let data = legacy_message.decryptAppleMessagePayload(body, withSignalingKey: signalingKey) {
            self.serialized = NSData(data: data)
        } else {
            print("LegacyMessage failed")
            self.serialized = NSData()
        }
    }
}

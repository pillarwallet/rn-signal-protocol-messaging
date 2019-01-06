#import <Foundation/Foundation.h>

@interface legacy_message : NSObject

typedef NS_ENUM(NSInteger, TSMACType) {
    TSHMACSHA256Truncated10Bytes = 2,
    TSHMACSHA256AttachementType  = 3
};

+ (nullable NSData *)computeSHA256HMAC:(NSData *)data withHMACKey:(NSData *)HMACKey;

+ (nullable NSData *)truncatedSHA256HMAC:(NSData *)dataToHMAC
                             withHMACKey:(NSData *)HMACKey
                              truncation:(NSUInteger)truncation;

+ (nullable NSData *)decryptAppleMessagePayload:(NSString *)body withSignalingKey:(NSString *)signalingKeyString;

@end

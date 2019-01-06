#import "legacy_message.h"
#import "NSData+OWS.h"
#import <CommonCrypto/CommonCryptor.h>
#import <CommonCrypto/CommonHMAC.h>

NS_ASSUME_NONNULL_BEGIN

#define HMAC256_KEY_LENGTH 32
#define HMAC256_OUTPUT_LENGTH 32
#define AES_CBC_IV_LENGTH 16
#define AES_KEY_SIZE 32

@implementation legacy_message

static const NSUInteger kHMAC256_EnvelopeKeyLength = 20;

+ (nullable NSData *)computeSHA256HMAC:(NSData *)data withHMACKey:(NSData *)HMACKey
{
    if (data.length >= SIZE_MAX) {
        printf("data is too long.");
        return nil;
    }
    size_t dataLength = (size_t)data.length;
    if (HMACKey.length >= SIZE_MAX) {
        printf("HMAC key is too long.");
        return nil;
    }
    size_t hmacKeyLength = (size_t)HMACKey.length;
    
    NSMutableData *_Nullable ourHmacData = [[NSMutableData alloc] initWithLength:CC_SHA256_DIGEST_LENGTH];
    if (!ourHmacData) {
        printf("could not allocate buffer.");
        return nil;
    }
    CCHmac(kCCHmacAlgSHA256, [HMACKey bytes], hmacKeyLength, [data bytes], dataLength, ourHmacData.mutableBytes);
    return [ourHmacData copy];
}

+ (nullable NSData *)truncatedSHA256HMAC:(NSData *)dataToHMAC
                             withHMACKey:(NSData *)HMACKey
                              truncation:(NSUInteger)truncation
{
    assert(truncation <= CC_SHA256_DIGEST_LENGTH);
    assert(dataToHMAC);
    assert(HMACKey);
    
    return
    [[legacy_message computeSHA256HMAC:dataToHMAC withHMACKey:HMACKey] subdataWithRange:NSMakeRange(0, truncation)];
}

+ (nullable NSData *)computeSHA256Digest:(NSData *)data
{
    return [self computeSHA256Digest:data truncatedToBytes:CC_SHA256_DIGEST_LENGTH];
}

+ (nullable NSData *)computeSHA256Digest:(NSData *)data truncatedToBytes:(NSUInteger)truncatedBytes
{
    if (data.length >= UINT32_MAX) {
        printf("data is too long.");
        return nil;
    }
    uint32_t dataLength = (uint32_t)data.length;
    
    NSMutableData *_Nullable digestData = [[NSMutableData alloc] initWithLength:CC_SHA256_DIGEST_LENGTH];
    if (!digestData) {
        printf("could not allocate buffer.");
        return nil;
    }
    CC_SHA256(data.bytes, dataLength, digestData.mutableBytes);
    return [digestData subdataWithRange:NSMakeRange(0, truncatedBytes)];
}

+ (nullable NSData *)decryptCBCMode:(NSData *)dataToDecrypt
                                key:(NSData *)key
                                 IV:(NSData *)iv
                            version:(nullable NSData *)version
                            HMACKey:(NSData *)hmacKey
                           HMACType:(TSMACType)hmacType
                       matchingHMAC:(NSData *)hmac
                             digest:(nullable NSData *)digest
{
    assert(dataToDecrypt);
    assert(key);
    if (key.length != kCCKeySizeAES256) {
        printf("key had wrong size.");
        return nil;
    }
    assert(iv);
    if (iv.length != kCCBlockSizeAES128) {
        printf("iv had wrong size.");
        return nil;
    }
    assert(hmacKey);
    assert(hmac);
    
    size_t bufferSize;
    BOOL didOverflow = __builtin_add_overflow(dataToDecrypt.length, kCCBlockSizeAES128, &bufferSize);
    if (didOverflow) {
        printf("bufferSize was too large.");
        return nil;
    }
    
    // Verify hmac of: version? || iv || encrypted data
    
    NSUInteger dataToAuthLength = 0;
    if (__builtin_add_overflow(dataToDecrypt.length, iv.length, &dataToAuthLength)) {
        printf("dataToAuth was too large.");
        return nil;
    }
    if (version != nil && __builtin_add_overflow(dataToAuthLength, version.length, &dataToAuthLength)) {
        printf("dataToAuth was too large.");
        return nil;
    }
    
    NSMutableData *dataToAuth = [NSMutableData data];
    if (version != nil) {
        [dataToAuth appendData:version];
    }
    [dataToAuth appendData:iv];
    [dataToAuth appendData:dataToDecrypt];
    
    NSData *_Nullable ourHmacData;
    
    if (hmacType == TSHMACSHA256Truncated10Bytes) {
        // used to authenticate envelope from websocket
        assert(hmacKey.length == kHMAC256_EnvelopeKeyLength);
        ourHmacData = [legacy_message truncatedSHA256HMAC:dataToAuth withHMACKey:hmacKey truncation:10];
        assert(ourHmacData.length == 10);
    } else if (hmacType == TSHMACSHA256AttachementType) {
        assert(hmacKey.length == HMAC256_KEY_LENGTH);
        ourHmacData =
        [legacy_message truncatedSHA256HMAC:dataToAuth withHMACKey:hmacKey truncation:HMAC256_OUTPUT_LENGTH];
        assert(ourHmacData.length == HMAC256_OUTPUT_LENGTH);
    } else {
        printf("unknown HMAC scheme: %ld", (long)hmacType);
    }
    
//    if (hmac == nil || ![ourHmacData ows_constantTimeIsEqualToData:hmac]) {
//        printf("Bad HMAC on decrypting payload.");
//        // Don't log HMAC in prod
//        return nil;
//    }
    
    // Optionally verify digest of: version? || iv || encrypted data || hmac
    if (digest) {
        printf("verifying their digest");
        [dataToAuth appendData:ourHmacData];
        NSData *_Nullable ourDigest = [legacy_message computeSHA256Digest:dataToAuth];
        if (!ourDigest || ![ourDigest ows_constantTimeIsEqualToData:digest]) {
            printf("Bad digest on decrypting payload");
            // Don't log digest in prod
            return nil;
        }
    }
    
    // decrypt
    NSMutableData *_Nullable bufferData = [NSMutableData dataWithLength:bufferSize];
    if (!bufferData) {
        printf("Failed to allocate buffer.");
        return nil;
    }
    
    size_t bytesDecrypted       = 0;
    CCCryptorStatus cryptStatus = CCCrypt(kCCDecrypt,
                                          kCCAlgorithmAES128,
                                          kCCOptionPKCS7Padding,
                                          [key bytes],
                                          [key length],
                                          [iv bytes],
                                          [dataToDecrypt bytes],
                                          [dataToDecrypt length],
                                          bufferData.mutableBytes,
                                          bufferSize,
                                          &bytesDecrypted);
    if (cryptStatus == kCCSuccess) {
        return [bufferData subdataWithRange:NSMakeRange(0, bytesDecrypted)];
    } else {
        printf("Failed CBC decryption");
    }
    
    return nil;
}

+ (nullable NSData *)decryptAppleMessagePayload:(NSString *)body withSignalingKey:(NSString *)signalingKeyString
{
    assert(body);
    assert(signalingKeyString);
    
    NSData *payload = [NSData dataFromBase64String:body];
    assert(payload);
    
    size_t versionLength = 1;
    size_t ivLength = 16;
    size_t macLength = 10;
    size_t nonCiphertextLength = versionLength + ivLength + macLength;
    
    size_t ciphertextLength;
    BOOL _didOverflow = __builtin_sub_overflow(payload.length, nonCiphertextLength, &ciphertextLength);
    assert(!_didOverflow);
    
    if (payload.length < nonCiphertextLength) {
        printf("Invalid payload");
        return nil;
    }
    if (payload.length >= MIN(SIZE_MAX, NSUIntegerMax) - nonCiphertextLength) {
        printf("Invalid payload");
        return nil;
    }
    
    NSUInteger cursor = 0;
    NSData *versionData = [payload subdataWithRange:NSMakeRange(cursor, versionLength)];
    cursor += versionLength;
    NSData *ivData = [payload subdataWithRange:NSMakeRange(cursor, ivLength)];
    cursor += ivLength;
    NSData *ciphertextData = [payload subdataWithRange:NSMakeRange(cursor, ciphertextLength)];
//    ows_add_overflow(cursor, ciphertextLength, &cursor);
    NSData *macData = [payload subdataWithRange:NSMakeRange(cursor, macLength)];
    
    NSData *signalingKey                = [NSData dataFromBase64String:signalingKeyString];
    NSData *signalingKeyAESKeyMaterial  = [signalingKey subdataWithRange:NSMakeRange(0, 32)];
    NSData *signalingKeyHMACKeyMaterial = [signalingKey subdataWithRange:NSMakeRange(32, kHMAC256_EnvelopeKeyLength)];
    return [legacy_message decryptCBCMode:ciphertextData
                                    key:signalingKeyAESKeyMaterial
                                     IV:ivData
                                version:versionData
                                HMACKey:signalingKeyHMACKeyMaterial
                               HMACType:TSHMACSHA256Truncated10Bytes
                           matchingHMAC:macData
                                 digest:nil];
}

@end

NS_ASSUME_NONNULL_END

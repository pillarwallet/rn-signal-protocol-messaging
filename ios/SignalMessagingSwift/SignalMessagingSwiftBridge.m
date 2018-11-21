//
//  SignalMessagingSwiftBridge.m
//  SignalMessagingSwift
//
//  Created by Mantas on 09/07/2018.
//  Copyright © 2018 Pillar. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "React/RCTBridgeModule.h"

@interface RCT_EXTERN_REMAP_MODULE(SignalClient, RNSignalClientModule, NSObject)

RCT_EXTERN_METHOD(createClient:(NSString *)username password:(NSString *)password host:(NSString *)host resolver: (RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(registerAccount: (RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(resetAccount: (RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(setFcmId:(NSString *)fcmId resolver: (RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(addContact:(NSString *)username resolver: (RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(deleteContact:(NSString *)username resolver: (RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(deleteContactMessages:(NSString *)username messageTag:(NSString *)messageTag resolver: (RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(receiveNewMessagesByContact:(NSString *)username messageTag:(NSString *)messageTag resolver: (RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(getMessagesByContact:(NSString *)username messageTag:(NSString *)messageTag resolver: (RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(getExistingMessages:(NSString *)messageTag resolve:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(getUnreadMessagesCount:(NSString *)messageTag resolve:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(sendMessageByContact:(NSString *)username messageString:(NSString *)messageString messageTag:(NSString *)messageTag resolver: (RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(sendSilentMessageByContact:(NSString *)username messageString:(NSString *)messageString messageTag:(NSString *)messageTag resolver: (RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);

@end

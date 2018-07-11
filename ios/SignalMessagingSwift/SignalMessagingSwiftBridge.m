//
//  SignalMessagingSwiftBridge.m
//  SignalMessagingSwift
//
//  Created by Mantas on 09/07/2018.
//  Copyright © 2018 Pillar. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "React/RCTBridgeModule.h"

@interface RCT_EXTERN_MODULE(RNSignalClientModule, NSObject)

RCT_EXTERN_METHOD(createClient:(NSString *)username password:(NSString *)password host:(NSString *)host);
RCT_EXTERN_METHOD(registerAccount: (RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(resetAccount: (RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(addContact:(NSString *)username resolve: (RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(deleteContact:(NSString *)username resolve: (RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);

RCT_EXTERN_METHOD(receiveNewMessageByContact:(NSString *)username resolve: (RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(getReceivedMessagesByContact:(NSString *)username resolve: (RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject;
RCT_EXTERN_METHOD(getUnreadMessagesCount:(NSString *)username resolve: (RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);
RCT_EXTERN_METHOD(sendMessage:(NSString *)receiver messageString:(NSString *)messageString resolve: (RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject);

@end

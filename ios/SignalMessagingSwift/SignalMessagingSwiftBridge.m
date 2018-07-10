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
RCT_EXTERN_METHOD(registerAccount: (RCTResponseSenderBlock)callback);
RCT_EXTERN_METHOD(resetAccount: (RCTResponseSenderBlock)callback);
RCT_EXTERN_METHOD(addContact:(NSString *)username callback: (RCTResponseSenderBlock)callback);
RCT_EXTERN_METHOD(deleteContact:(NSString *)username callback: (RCTResponseSenderBlock)callback);

RCT_EXTERN_METHOD(receiveNewMessageByContact:(NSString *)username callback: (RCTResponseSenderBlock)callback);
RCT_EXTERN_METHOD(getReceivedMessagesByContact:(NSString *)username callback: (RCTResponseSenderBlock)callback);
RCT_EXTERN_METHOD(getUnreadMessagesCount:(NSString *)username callback: (RCTResponseSenderBlock)callback);
RCT_EXTERN_METHOD(sendMessage:(NSString *)receiver messageString:(NSString *)messageString callback: (RCTResponseSenderBlock)callback);

@end

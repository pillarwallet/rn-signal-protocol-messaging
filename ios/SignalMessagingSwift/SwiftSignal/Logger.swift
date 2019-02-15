//
//  Logger.swift
//  SignalMessagingSwift
//
//  Created by Deimantas on 15/02/2019.
//  Copyright © 2019 Pillar. All rights reserved.
//

import Foundation
import Sentry


class Logger: NSObject {
    
    func sendInfoMessage(message: String) {
        let event = Event(level: .info)
        event.message = message
        sendEvent(event: event)
    }
    
    func sendErrorMessage(message: String) {
        Client.shared?.snapshotStacktrace {
            let event = Event(level: .error)
            event.message = message
            Client.shared?.appendStacktrace(to: event)
            self.sendEvent(event: event)
        }
    }
    
    func sendEvent(event: Event) {
        Client.shared?.send(event: event)
    }
    
}

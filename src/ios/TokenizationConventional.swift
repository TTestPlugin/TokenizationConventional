import Foundation
import UIKit
import D1
import Security
/// D1 SDK iOS plugin entry point.
@objc(TokenizationConventional) class TokenizationConventional : CDVPlugin {
    
    private var d1Task: D1Task?
    
    struct CardJSON: Decodable {
        let cardID: String
        let productID: String
        let productTitle: String
    }
    
    @objc(configure:)
    func configure(command: CDVInvokedUrlCommand) {
        
        let digitalCardUrl = command.arguments[4] as! String
        let consumerId = command.arguments[5] as! String
        let serviceUrl = command.arguments[0] as! String
        let issuerId = command.arguments[1] as! String
        let exponent = command.arguments[2] as! String
        let rsaExponent = self.dataWithHexString(hex: exponent);
        let modulus = command.arguments[3] as! String
        let rsaModulus = self.dataWithHexString(hex: modulus);
        
        NSLog("Outsystems=====> Configure SDK Digital Card URL : \(digitalCardUrl) \n consumerId : \(consumerId) \n serviceUrl : \(serviceUrl) issuerId : \(issuerId) \n exponent : \(exponent) \n modulus : \(modulus) \n");
        
        self.configure(serviceUrl, issuerId, rsaExponent, rsaModulus, digitalCardUrl, consumerId, self.commandDelegate, command.callbackId)
        
    }
    
    func configure(_ d1ServiceURLString:String, _ issuerID:String, _ publicKeyExponent: Data, _ publicKeyModulus: Data, _ digitalCardURLString:String, _ consumerID: String, _ callback: CDVCommandDelegate, _ callbackId: String) -> Void {
        
        var comp = D1Task.Components()
        comp.d1ServiceURLString = d1ServiceURLString
        comp.issuerID = issuerID
        comp.d1ServiceRSAExponent = publicKeyExponent
        comp.d1ServiceRSAModulus = publicKeyModulus
        comp.digitalCardURLString = digitalCardURLString
        d1Task = comp.task()
        
        let coreConfig = ConfigParams.coreConfig(consumerID: consumerID)
        // required for Card Processing & OEM Pay
        let cardConfig = ConfigParams.cardConfig()
        
        getD1Task().configure([coreConfig, cardConfig]) { errors in
            
            if let jsonError = self.createErrorJson(errors) {
                NSLog("Outsystems=====> Configure SDK Error : "+jsonError);
                let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "D1 SDK Configuration Error : "+jsonError);
                callback.send(pluginResult, callbackId: callbackId);
                return
            }
            
            NSLog("Outsystems=====> D1 SDK Configuration Successfull");
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK,messageAs: "D1 SDK Configuration Successfull");
            callback.send(pluginResult, callbackId: callbackId);
        }
    }
    
    @objc(walletExtensionConfig:)
    func walletExtensionConfig(command: CDVInvokedUrlCommand) {
        
        NSLog("Outsystems=====> Configuring wallet extension")
        
        guard let consumerId = command.arguments[0] as? String else {
            logAndSendError("Invalid consumerID", command: command)
            return
        }
        
        guard let cardJsonDetails = command.arguments[1] as? String else {
            logAndSendError("Invalid card details", command: command)
            return
        }
    
        if saveToKeychain(key: "D1ConsumerID", value: consumerId) {
            NSLog("Outsystems=====> ConsumerID saved to Keychain")
        } else {
            NSLog("Outsystems=====> Failed to save ConsumerID to Keychain")
        }
        
        let appGroupID = "group.com.ahlibank.om"
        if let containerURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupID) {
            NSLog("Outsystems=====> App Group is configured correctly. Container URL: \(containerURL)")
        } else {
            NSLog("Outsystems=====> Error: App Group is not configured correctly.")
        }
        
        guard let jsonData = cardJsonDetails.data(using: .utf8) else {
            logAndSendError("Failed to convert card details to JSON data", command: command)
            return
        }
        
        NSLog("Outsystems=====> Configuring wallet extension for consumerID: \(consumerId), appGroupID: \(appGroupID), cardJsonDetails: \(cardJsonDetails)")
        
        do {
            let coreConfig = ConfigParams.coreConfig(consumerID: consumerId)
            let cardConfig = ConfigParams.cardConfig()
            // Parse the JSON data
            let parsedCards = try JSONDecoder().decode([CardJSON].self, from: jsonData)
            // Convert parsed data into an array of ConfigParams.CardParams
            let cardList: [ConfigParams.CardParams] = parsedCards.map { card in
                ConfigParams.CardParams(
                    cardID: card.cardID,
                    cardArt: UIImage(named: getCardImage(productID: card.productID, cardId: card.cardID) ?? "MK6") ?? UIImage(), // Use productID as image name
                    productTitle: card.productTitle
                )
            }
            
            NSLog("Outsystems=====> Card list count : \(cardList.count)")
            NSLog("Outsystems=====> Card list : \(cardList)")
            
            // Assign the card list to walletExtensionConfig
            let walletExtensionConfig = ConfigParams.walletExtensionConfig(
                cardParamsList: cardList,
                appGroupID: appGroupID
            )
            
            //Assign the coreconfig, cardconfig and walletExtensionConfig
            getD1Task().configure([coreConfig, cardConfig, walletExtensionConfig]) { errors in
                if let errors = errors {
                    for error in errors {
                        // Check error details to see whose config cause the errors,
                        // refer to D1 SDK Integration – Error Management section.
                        self.logAndSendError("Configure wallet extension got failed with error: \(error.localizedDescription)", command: command)
                        break
                    }
                }else{
                    self.logAndSendSuccess("Configure wallet extension completed for Consumer ID: \(consumerId)", command: command)
                }
            }
        } catch {
            NSLog("Outsystems=====> Failed to parse or create card list: \(error.localizedDescription)")
        }
    }
	
	@objc(disableWalletExtension:)
    func disableWalletExtension(command: CDVInvokedUrlCommand) {
        
        NSLog("Outsystems=====> Disable wallet extension")
        
        guard let consumerId = command.arguments[0] as? String else {
            logAndSendError("Invalid consumerID", command: command)
            return
        }
        
        let coreConfig = ConfigParams.coreConfig(consumerID: consumerId)
        let cardConfig = ConfigParams.cardConfig()
        let walletExtensionConfig = ConfigParams.walletExtensionConfig(cardParamsList: [],appGroupID: "group.com.ahlibank.om")
        getD1Task().configure([coreConfig, cardConfig, walletExtensionConfig]) { errors in
            if let errors = errors {
                for error in errors {
                    // Check error details to see whose config cause the errors,
                    // refer to D1 SDK Integration – Error Management section.
                    self.logAndSendError("Disable wallet extension got failed with error: \(error.localizedDescription)", command: command)
                    break
                }
            }else{
                self.logAndSendSuccess("Disable wallet extension completed for Consumer ID: \(consumerId)", command: command)
            }
        }
    }
    
    private func logAndSendError(_ message: String, command: CDVInvokedUrlCommand) {
        NSLog("Outsystems=====> \(message)")
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: message)
        commandDelegate.send(pluginResult, callbackId: command.callbackId)
    }
    
    private func logAndSendSuccess(_ message: String, command: CDVInvokedUrlCommand) {
        NSLog("Outsystems=====> \(message)")
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: message)
        commandDelegate.send(pluginResult, callbackId: command.callbackId)
    }
    
    private func getCardImage(productID: String, cardId: String) -> String? {
        let productIDMapped: String
        let cardIDMapped: String
        cardIDMapped = cardId
        
        switch productID {
        case "IPS", "MNS":
            productIDMapped = "IPS-MNS"
        case "VPP", "MNP":
            productIDMapped = "VPP-MNP"
        default:
            productIDMapped = productID
        }
        
        guard let _ = UIImage(named: productIDMapped) else {
            NSLog("Outsystems=====> Card image not found for product ID: \(productIDMapped) Card id \(cardIDMapped)")
            return nil
        }
        
        NSLog("Outsystems=====> Card image found for product ID: \(productIDMapped) Card id \(cardIDMapped)")
        return productIDMapped
    }

    
    func saveToKeychain(key: String, value: String) -> Bool {
        guard let valueData = value.data(using: .utf8) else {
            NSLog("Outsystems=====> Error: Unable to convert value to Data.")
            return false
        }
        
        // Define the query
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: KeychainConstants.service,
            kSecAttrAccessGroup as String: KeychainConstants.accessGroup,
            kSecAttrAccount as String: key
        ]
        
        // Check if the item already exists
        var addQuery = query
        addQuery[kSecValueData as String] = valueData
        
        // Add the item to the keychain
        let status = SecItemAdd(addQuery as CFDictionary, nil)
        
        if status == errSecDuplicateItem {
            // Update the existing item if it already exists
            let updatedData: [String: Any] = [kSecValueData as String: valueData]
            let updateStatus = SecItemUpdate(query as CFDictionary, updatedData as CFDictionary)
            
            if updateStatus != errSecSuccess {
                NSLog("Outsystems=====> Error updating Keychain item: \(updateStatus)")
                return false
            } else {
                NSLog("Outsystems=====> Keychain item updated successfully.")
            }
        } else if status != errSecSuccess {
            NSLog("Outsystems=====> Error adding Keychain item: \(status)")
            return false
        } else {
            NSLog("Outsystems=====> Keychain item added successfully.")
        }
        
        return true
    }
    
    // Constants for Keychain attributes
    struct KeychainConstants {
        static let service = "IssuerAppService"
        static let accessGroup = "group.com.ahlibank.om"
    }
    
    func loadFromKeychain(key: String) -> String? {
        let getQuery: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                       kSecAttrService as String: KeychainConstants.service,
                                       kSecAttrAccessGroup as String: KeychainConstants.accessGroup,
                                       kSecAttrAccount as String: key,
                                       kSecReturnData as String: true]
        var item: AnyObject?
        let status = SecItemCopyMatching(getQuery as CFDictionary, &item)
        if status == errSecSuccess,
           let data = item as? Data {
            return String(data: data, encoding: .utf8)
        }
        return nil
    }
    
    func dataWithHexString(hex: String) -> Data {
        var hex = hex
        var data = Data()
        while(hex.count > 0) {
            let subIndex = hex.index(hex.startIndex, offsetBy: 2)
            let c = String(hex[..<subIndex])
            hex = String(hex[subIndex...])
            var ch: UInt64  = 0
            Scanner(string: c).scanHexInt64(&ch)
            var char = UInt8(ch)
            data.append(&char, count: 1)
        }
        
        return data
    }
    
    @objc(login:)
    func login(command: CDVInvokedUrlCommand) {
        let issuerToken = command.arguments[0] as! String
        self.login(issuerToken, self.commandDelegate, command.callbackId)
    }
    
    private func login(_ issueToken: String, _ callback: CDVCommandDelegate, _ callbackId: String) -> Void {
        if var issueTokenData: Data = issueToken.data(using: .utf8) {
            self.getD1Task().login(&issueTokenData) { error in
                
                
                if let jsonError = self.createErrorJson(error) {
                    NSLog("Outsystems=====> Login SDK Error : "+jsonError);
                    let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "D1 Login Error : "+jsonError);
                    callback.send(pluginResult, callbackId: callbackId);
                    return
                }
                
                NSLog("Outsystems=====> Login SDK Success");
                let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK,messageAs: "D1 Login Success");
                callback.send(pluginResult, callbackId: callbackId);
            }
        }
    }
    
    @objc(checkCardDigitizationState:)
	func checkCardDigitizationState(command: CDVInvokedUrlCommand) {
    
    guard let cardId = command.arguments.first as? String else {
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "Invalid Card ID")
        self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
        return
    }
    
    NSLog("Outsystems=====> Checking Card Digitization State for CardId: \(cardId)")
    
    getD1Task().cardDigitizationState(cardId) { [weak self] result, error in
        guard let self = self else { return }
        
        if let error = error, let jsonError = self.createErrorJson(error) {
            NSLog("Outsystems=====> Card Digitization State Error: \(jsonError)")
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: jsonError)
            self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
            return
        }
        
        guard let result = result else {
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "No result returned")
            self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
            return
        }
        
        let stateMessage: String
        switch result.state {
        case .pendingIDVLocal:
            stateMessage = "pendingIDVLocal"
        case .pendingIDVRemote:
            stateMessage = "pendingIDVRemote"
        case .notDigitized:
            stateMessage = "notDigitized"
        case .digitized:
            stateMessage = "digitized"
        @unknown default:
            stateMessage = "unknownState"
            NSLog("Outsystems=====> Card Digitization State: Unknown State Encountered")
        }
        
        NSLog("Outsystems=====> Card Digitization State: \(stateMessage)")
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: stateMessage)
        self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
    }
	}

    
    @objc(addToAppleWallet:)
    func addToAppleWallet(command: CDVInvokedUrlCommand){
        let cardId = command.arguments[0] as! String
        NSLog("Outsystems=====> Add to apple wallet "+cardId);
        getD1Task().addDigitalCardToOEM(cardId, viewController: self.viewController) { error in
            if let error = error {
                // Refer to D1 SDK Integration – Error Management section.
                NSLog("Outsystems=====> Add to apple wallet on error : "+error.localizedDescription);
                let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: error.localizedDescription);
                self.commandDelegate.send(pluginResult, callbackId: command.callbackId);
                
            } else {
                // Proceed with subsequent flows. For example, update UI.
                NSLog("Outsystems=====> Add to apple wallet success");
                let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: "Add to apple wallet success");
                self.commandDelegate.send(pluginResult, callbackId: command.callbackId);
            }
        }
    }
    
    private func getD1Task() -> D1Task {
        if let d1Task = d1Task {
            return d1Task
        } else {
            fatalError("Need to configure D1 SDK first.")
        }
    }
    
    private func createErrorJson(_ error: [D1Error]?) -> String? {
        if let error = error {
            do {
                var dic = [String]()
                for i in 0..<error.count {
                    dic.append(error[i].localizedDescription)
                    //dic.append(D1Error(code: error[i].code.rawValue, message: error[i].localizedDescription))
                }
                let jsonEncoder = JSONEncoder()
                let jsonData = try jsonEncoder.encode(dic)
                let json = String(data: jsonData, encoding: String.Encoding.utf8)
                
                return json
            } catch {
                // should not happen
                fatalError()
            }
        }
        
        return nil
    }
    
    private func createErrorJson(_ error: D1Error?) -> String? {
        if let error = error {
            do {
                let jsonEncoder = JSONEncoder()
                let jsonData = try jsonEncoder.encode(error.localizedDescription)
                let jsonRD1Error = String(data: jsonData, encoding: String.Encoding.utf8)
                
                return jsonRD1Error
            } catch {
                // this should not happen
                fatalError()
            }
        }
        
        return nil
    }
}

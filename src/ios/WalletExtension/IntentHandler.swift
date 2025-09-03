 
import D1
 
 
/// Non UI Apple Wallet Extension  for available cards retrieval.
class IntentHandler: D1IssuerProvisioningExtensionHandler {
 
    override func errorEncountered(_ error: D1Error) {
        print("Error = \(error.localizedDescription)")
    }
}
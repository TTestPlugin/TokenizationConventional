import UIKit
import PassKit
import D1
import LocalAuthentication

// ViewController to authenticate user for D1Push card provisioning using Apple Wallet Extension.
@available(iOS 14.0, *)
class IntentViewController: UIViewController, D1IssuerProvisioningExtensionAuthorizationProviding {
    var completionHandler: ((PKIssuerProvisioningExtensionAuthorizationResult) -> Void)?
    private let authButton = UIButton(type: .system)
    private let imageView = UIImageView()
    private let activityIndicator = UIActivityIndicatorView(style: .large)
    private var d1Task: D1Task?

    override func viewDidLoad() {
        super.viewDidLoad()
        
        // Configure UI layout
        configureUI()
    }

    private func configureUI() {
        
        self.view.backgroundColor = .white
        /* configure UI layout */
        
        // Set the button's title
        self.authButton.setTitle("Authenticate", for: .normal)
        self.authButton.setTitleColor(.white, for: .normal)
        self.authButton.titleLabel?.font = UIFont.boldSystemFont(ofSize: 18)
        
        // Set the button's background color
        self.authButton.backgroundColor = UIColor(red: 16/255, green: 104/255, blue: 235/255, alpha: 1.0)
        
        // Set the button's corner radius for rounded corners
        self.authButton.layer.cornerRadius = 10
        
        // Optional: Add shadow for better visual appearance
        self.authButton.layer.shadowColor = UIColor.black.cgColor
        self.authButton.layer.shadowOpacity = 0.2
        self.authButton.layer.shadowOffset = CGSize(width: 0, height: 2)
        self.authButton.layer.shadowRadius = 5
        
        // Disable autoresizing mask translation to use Auto Layout
        self.authButton.translatesAutoresizingMaskIntoConstraints = false
        
        // Add the button to the view
        self.view.addSubview(self.authButton)
        
        // Center the button using Auto Layout
        NSLayoutConstraint.activate([
            self.authButton.centerXAnchor.constraint(equalTo: self.view.centerXAnchor),
            self.authButton.centerYAnchor.constraint(equalTo: self.view.centerYAnchor),
            self.authButton.widthAnchor.constraint(equalToConstant: 200),
            self.authButton.heightAnchor.constraint(equalToConstant: 50)
        ])
        
        // Add an action for the button
        self.authButton.addTarget(self, action: #selector(authButtonTouched), for: .touchUpInside)
        
        
        self.activityIndicator.translatesAutoresizingMaskIntoConstraints = false
        self.activityIndicator.color = UIColor.black
        self.view.addSubview(activityIndicator)
        self.activityIndicator.centerXAnchor.constraint(equalTo: view.centerXAnchor).isActive = true
        self.activityIndicator.centerYAnchor.constraint(equalTo: view.centerYAnchor).isActive = true
        
        // Ensure the image is loaded correctly
        if let image = UIImage(named: "ABOlogo.png") {
            self.imageView.image = image
        } else {
            NSLog("Outsystems=====> Image not found or could not be loaded.")
        }
        
        // Setup ImageView (Ensure it has a frame via constraints)
        self.imageView.contentMode = .scaleAspectFit
        self.imageView.translatesAutoresizingMaskIntoConstraints = false
        let horizontalConstraintImageView = NSLayoutConstraint(item: self.imageView,
                                                               attribute: NSLayoutConstraint.Attribute.centerX,
                                                               relatedBy: NSLayoutConstraint.Relation.equal,
                                                               toItem: self.view,
                                                               attribute: NSLayoutConstraint.Attribute.centerX,
                                                               multiplier: 1,
                                                               constant: 0)
        let verticalConstraintImageView = NSLayoutConstraint(item: self.imageView,
                                                             attribute: NSLayoutConstraint.Attribute.top,
                                                             relatedBy: NSLayoutConstraint.Relation.equal,
                                                             toItem: self.view,
                                                             attribute: NSLayoutConstraint.Attribute.top,
                                                             multiplier: 1,
                                                             constant: 100)
        
        let verticalConstraintImageViewHeight = NSLayoutConstraint(item: self.imageView,
                                                                   attribute: NSLayoutConstraint.Attribute.height,
                                                                   relatedBy: NSLayoutConstraint.Relation.equal,
                                                                   toItem: nil,
                                                                   attribute: NSLayoutConstraint.Attribute.notAnAttribute,
                                                                   multiplier: 1,
                                                                   constant: 200)
        
        let verticalConstraintImageViewWidth = NSLayoutConstraint(item: self.imageView,
                                                                  attribute: NSLayoutConstraint.Attribute.width,
                                                                  relatedBy: NSLayoutConstraint.Relation.equal,
                                                                  toItem: nil,
                                                                  attribute: NSLayoutConstraint.Attribute.notAnAttribute,
                                                                  multiplier: 1,
                                                                  constant: 200)
        
        self.view.addSubview(self.imageView)
        self.view.addConstraints([horizontalConstraintImageView,
                                  verticalConstraintImageView,
                                  verticalConstraintImageViewHeight,
                                  verticalConstraintImageViewWidth])
    }

    @objc func authButtonTouched() {
        // Optionally, load authentication-related data from keychain, e.g., consumerID
		
        authenticateUser()
		
		// Provide both manual and biometric login methods based on Apple functional requirements
        // ...
    }
    
    func authenticateUser() {
        let context = LAContext()
        var error: NSError?
        
        // Check if the device supports Face ID/Touch ID
        if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
            // Determine the type of biometric authentication supported
            let reason = "Authenticate using Face ID or Touch ID"
            
            context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { success, authenticationError in
                DispatchQueue.main.async {
                    if success {
                        NSLog("Outsystems=====> Authentication successful!")
                        // Perform actions after successful authentication
                        self.activityIndicator.startAnimating()
                        self.getJWTToken();
                    } else {
                        NSLog("Outsystems=====> Authentication failed: \(authenticationError?.localizedDescription ?? "Unknown error")")
                        // Handle failure
                    }
                }
            }
        } else {
            // Handle devices that don't support Face ID/Touch ID
            NSLog("Outsystems=====> Biometric authentication not available: \(error?.localizedDescription ?? "Unknown error")")
        }
    }
    

    private func getJWTToken() {
	
		guard let consumerID = loadFromKeychain(key: "D1ConsumerID") else {
            // Log or handle the case where consumerId is not available
            NSLog("Outsystems=====> Error: consumerId not found in keychain")
            self.activityIndicator.stopAnimating()
            self.completionHandler?(.canceled)
            return
        }
	
		let urlString = "https://mye-bankonline.ahlibank.om/Common_MBL/rest/Thales/GetThalesJWTToken?ConsumerID=\(consumerID)"
        guard let url = URL(string: urlString) else {
            NSLog("Invalid URL")
            self.activityIndicator.stopAnimating()
            self.completionHandler?(.canceled)
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "GET"

        let task = URLSession.shared.dataTask(with: request) { data, response, error in
            guard let data = data, error == nil else {
                NSLog("Rest API Error: \(error?.localizedDescription ?? "Unknown error")")
                self.activityIndicator.stopAnimating()
                self.completionHandler?(.canceled)
                return
            }

            do {
                if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let jwtToken = json["AccessToken"] as? String {
                   self.loginD1SDK(jwtToken)
                } else {
                    NSLog("AccessToken not found or invalid format")
                    self.activityIndicator.stopAnimating()
                    self.completionHandler?(.canceled)
                }
            } catch {
                NSLog("Parsing Error: \(error.localizedDescription)")
                self.activityIndicator.stopAnimating()
                self.completionHandler?(.canceled)
            }
        }
        task.resume()
    }

    private func loginD1SDK(_ issueToken: String) -> Void {
        if var issueTokenData: Data = issueToken.data(using: .utf8) {
            login(&issueTokenData) { [weak self] error in
                self?.activityIndicator.stopAnimating()
                
                if (error != nil) {
                    NSLog("Outsystems=====> Login Error : "+error!.localizedDescription)
                    self?.completionHandler?(.canceled)
                } else {
                    NSLog("Outsystems=====> Login Success")
                    self?.completionHandler?(.authorized)
                }
            }
        }
    }
    
    func dataWithHexString(hex: String) -> Data {
        var hex = hex
        var data = Data()
        while hex.count > 0 {
            let c = String(hex.prefix(2))
            hex = String(hex.dropFirst(2))
            var ch: UInt64 = 0
            Scanner(string: c).scanHexInt64(&ch)
            data.append(UInt8(ch))
        }
        return data
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
    
    private func loadFromKeychain(key: String) -> String? {
        let getQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "IssuerAppService",
            kSecAttrAccessGroup as String: "group.com.ahlibank.om",
            kSecAttrAccount as String: key,
            kSecReturnData as String: true
        ]
        var item: AnyObject?
        let status = SecItemCopyMatching(getQuery as CFDictionary, &item)
        if status == errSecSuccess, let data = item as? Data {
            return String(data: data, encoding: .utf8)
        }
        return nil
    }

    func showAlert(title: String, message: String) {
        let alertController = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alertController.addAction(UIAlertAction(title: "OK", style: .default, handler: nil))
        present(alertController, animated: true, completion: nil)
    }
}

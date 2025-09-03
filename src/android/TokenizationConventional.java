package com.ahlibank.tokenization;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.CardEmulation;
import android.os.Build;
import android.util.Base64;
import android.util.Log;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.gson.GsonBuilder;
import com.huawei.hms.aaid.HmsInstanceId;
import com.huawei.hms.api.ConnectionResult;
import com.huawei.hms.api.HuaweiApiAvailability;
import com.huawei.hms.push.HmsMessaging;
import com.thalesgroup.gemalto.d1.ConfigParams;
import com.thalesgroup.gemalto.d1.D1Exception;
import com.thalesgroup.gemalto.d1.D1Params;
import com.thalesgroup.gemalto.d1.D1Task;
import com.thalesgroup.gemalto.d1.card.AssetContent;
import com.thalesgroup.gemalto.d1.card.CardAction;
import com.thalesgroup.gemalto.d1.card.CardAsset;
import com.thalesgroup.gemalto.d1.card.CardDigitizationState;
import com.thalesgroup.gemalto.d1.card.CardMetadata;
import com.thalesgroup.gemalto.d1.card.D1PushWallet;
import com.thalesgroup.gemalto.d1.card.OEMPayType;
import com.thalesgroup.gemalto.d1.card.State;
import com.thalesgroup.gemalto.d1.d1pay.AuthenticationParameter;
import com.thalesgroup.gemalto.d1.d1pay.ContactlessTransactionListener;
import com.thalesgroup.gemalto.d1.d1pay.D1HCEService;
import com.thalesgroup.gemalto.d1.d1pay.D1PayConfigParams;
import com.thalesgroup.gemalto.d1.d1pay.D1PayDigitalCard;
import com.thalesgroup.gemalto.d1.d1pay.D1PayWallet;
import com.thalesgroup.gemalto.d1.d1pay.DeviceAuthenticationCallback;
import com.thalesgroup.gemalto.d1.d1pay.DeviceAuthenticationTimeoutCallback;
import com.thalesgroup.gemalto.d1.d1pay.TransactionData;
import com.thalesgroup.gemalto.d1.d1pay.VerificationMethod;
import com.thalesgroup.gemalto.d1.d1pay.TransactionHistory;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

/**
 * This class echoes a string called from JavaScript.
 */
public class TokenizationConventional extends CordovaPlugin {

    public D1Task mD1Task;
    private D1PayContactlessTransactionListener mD1PayTransactionListener;
    private CallbackContext callback;
    private static final String TAG = "Outsystems==>" + CoreUtils.class.getSimpleName();

    public String currentCardID;
	
	private static TokenizationConventional instance;
	public TokenizationConventional(){
        instance = this;
    }
	
	public static TokenizationConventional getInstance(){
        return instance;
    }
	
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        try {
            callback = callbackContext;
            setAppContext();
            switch (action) {
                case "setApplicationContext":
                    setAppContext();
                    break;

                case "configure":
                    final String serviceUrl = args.getString(0);
                    final String issuerId = args.getString(1);
                    String sExponent = String.valueOf(args.getString(2));
                    final byte[] exponent = sExponent.getBytes();
                    String sModulus = String.valueOf(args.getString(3));
                    final byte[] modulus = sModulus.getBytes();
                    final String digitalCardUrl = args.getString(4);
                    final String consumerId = args.getString(5);
					final String huaweiAppID = args.getString(6);
					final String samsungServiceID = args.getString(7);
                    final String visaClientAppId = args.getString(8);

                    Log.i(TAG, "D1 SDK Configure Info :\n" +
                            "serviceUrl: " + serviceUrl + "\n" +
                            "issuerId: " + issuerId + "\n" +
                            "exponent: " + sExponent + "\n" +
                            "modulus: " + sModulus + "\n" +
                            "digitalCardUrl: " + digitalCardUrl + "\n" +
                            "consumerId: " + consumerId + "\n" +
                            "samsungServiceID: "+samsungServiceID + "\n" +
                            "visaClientAppId: "+visaClientAppId);

                    configure(cordova.getContext(), cordova.getActivity(), serviceUrl, issuerId, exponent, modulus, digitalCardUrl, consumerId,samsungServiceID,visaClientAppId);
                    pushToken(huaweiAppID);
                    break;

                case "login":
                    final String token = args.getString(0);
                    login(token.getBytes(StandardCharsets.UTF_8));
                    break;

                case "checkCardDigitizationState":
                    checkCardDigitizationState(args.getString(0), callback);
                    break;

                case "addDigitalCard":
                    addDigitalCard(args.getString(0), callback);
                    break;

                case "getDigitalCardData":
                    getDigitalCardList();
                    break;

                case "getCardData":
                    getCardData(args.getString(0));
                    break;

                case "doManualPayment":
                    doManualPayment(args.getString(0));
                    break;

                case "setDefultPaymentCard":
                    setDefultPaymentCard(args.getString(0));
                    break;

                case "unSetDefaultPaymentCard":
                    unSetDefaultPaymentCard();
                    break;

                case "removeDigitalCard":
                    initiateRemoveCard(args.getString(0).trim());
                    break;

                case "getDeviceName":
                    callbackContext.success(getDeviceName());
                    break;

                case "checkNFCEnable":
                    checkNFCStatus();
                    break;

                case "checkDefaultPaymentApp":
                    checkDefaultPaymentApp();
                    break;

                case "setDefaultPaymentApp":
                    setDefaultPaymentApp();
                    break;
					
				case "getD1PayTxnHistory":
                    getD1PayTxnHistory(args.getString(0));
                    break;

                case "checkD1PushCardDigitizationState":
                    checkD1PushCardDigitizationStateSamsungPay(args.getString(0));
                    break;

                case "d1PushActivateDigitalCardSamsungPay":
                    d1PushActivateDigitalCardSamsungPay();
                    break;

                case "d1PushAddDigitalCardToSamsungPay":
                    d1PushAddDigitalCardToSamsungPay(args.getString(0));
                    break;
					
				case "getAppPkValue":
					String AppPKValue = getAppPkValue(cordova.getContext());
					Log.i(TAG,"App PK Value for Ahlibank : "+AppPKValue);
					break;
					
				case "deactivatePaymentState":
					mD1PayTransactionListener.deactivate();
					break;
					
				case "GPayCardState":
					checkD1PushCardDigitizationStateGPay(args.getString(0));
					break;

				case "AddCardGPay":
					addCardToGoogleWallet(args.getString(0));
					break;
					
                default:
                    callbackContext.error("undefined action");
                    return false;
            }
        } catch (Exception e) {
            Log.i(TAG, "Execute Exception : "+e.toString());
        }
        return true;
    }
	
	private void pushToken(String appID) {
        try {

            HuaweiApiAvailability apiAvailability = HuaweiApiAvailability.getInstance();
            int availability = apiAvailability.isHuaweiMobileServicesAvailable(cordova.getContext());

            if (availability == ConnectionResult.SUCCESS) {
                Log.i(TAG, "Huawei API available");
                getSetHMSToken(appID);
            } else {
                Log.i(TAG, "Huawei NOT API available");
            }

        } catch (Exception e) {
            Log.e(TAG, "pushToken Exception : " + e.toString());
        }
    }
	
	public static String getAppPkValue(Context context) {
        try {
            // Get PackageManager to fetch the app's package information
            PackageManager packageManager = context.getPackageManager();
            String packageName = context.getPackageName();

            // Log the package name for debugging
            Log.i(TAG, "Package Name = " + packageName);

            // Get the package info along with the signatures
            PackageInfo packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            android.content.pm.Signature signature = packageInfo.signatures[0];

            // Get the signature as a byte array
            byte[] signatureBytes = signature.toByteArray();
            Log.i(TAG, "signatureBytes = " + byte2Hex(signatureBytes));

            // Create a CertificateFactory instance to process the signature
            ByteArrayInputStream certInputStream = new ByteArrayInputStream(signatureBytes);
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X509");
            Certificate certificate = certificateFactory.generateCertificate(certInputStream);

            // Extract the public key from the certificate
            byte[] publicKeyEncode = certificate.getPublicKey().getEncoded();
            Log.i(TAG, "publicKeyEncode = " + byte2Hex(publicKeyEncode));

            // Use SHA-256 to hash the public key
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] sha256 = digest.digest(publicKeyEncode);
            String appPk = byte2Hex(sha256).toUpperCase();  // Convert to uppercase to match standard format

            // Log the appPk (SHA-256 hash of the public key)
            Log.i(TAG, "appPk = " + appPk);

            return appPk;  // Return the app's public key value (SHA-256 hash)

        } catch (Exception e) {
            Log.e(TAG, "getAppPkValue: " + e.toString());
        }
        return null;  // Return null in case of an error
    }

    private static String byte2Hex(byte[] input) {
        StringBuilder buf = new StringBuilder();
        char[] hex = new char[]{
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
        };
        for (byte b : input) {
            byte low = (byte) (b & 0x0F);
            byte high = (byte) ((b >> 4) & 0x0F);
            buf.append(hex[high]).append(hex[low]);
        }
        return buf.toString();
    }
	
	private void getSetHMSToken(String appID) {
        try {
            new Thread(() -> {
                try {
                    String appId = com.huawei.agconnect.config.AGConnectServicesConfig
                            .fromContext(cordova.getActivity().getApplicationContext())
                            .getString(appID); // From agconnect-services.json

                    String token = com.huawei.hms.aaid.HmsInstanceId
                            .getInstance(cordova.getActivity().getApplicationContext())
                            .getToken(appId, "HCM"); // "HCM" = Huawei Cloud Messaging
							

                    Log.i(TAG, "Huawei Push Token: " + "HMS:"+token);
                    mD1Task.updatePushToken("HMS:"+token, new D1Task.Callback<Void>() {
                        @Override
                        public void onSuccess(@Nullable Void ignored) {
                            // Proceed with subsequent flows.
                            Log.i(TAG, "HMS Push UpdatePushToken Success: " + "HMS:"+token);
                        }

                        @Override
                        public void onError(@NonNull D1Exception exception) {
                            // Refer to D1 SDK Integration – Error Management section.
                            Log.e(TAG, "HMS Push UpdatePushToken On Error: " + exception.toString());
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Failed to get Huawei push token", e);
                }
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "getSetHMSToken Exception : " + e.toString());
        }
    }

    private void getSetFirebaseToken() {
        try {
            FirebaseMessaging.getInstance().getToken()
                    .addOnCompleteListener(new OnCompleteListener<String>() {
                        @Override
                        public void onComplete(@NonNull Task<String> task) {
                            if (!task.isSuccessful()) {
                                Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                                return;
                            }

                            // Get new FCM registration token
                            String token = task.getResult();
                            Log.i(TAG, "Firebase token : " + token);

                            mD1Task.updatePushToken(token, new D1Task.Callback<Void>() {
                                @Override
                                public void onSuccess(@Nullable Void ignored) {
                                    // Proceed with subsequent flows.
                                    Log.i(TAG, "D1FirebaseService UpdatePushToken Success: " + token);
                                }

                                @Override
                                public void onError(@NonNull D1Exception exception) {
                                    // Refer to D1 SDK Integration – Error Management section.
                                    Log.e(TAG, "D1FirebaseService UpdatePushToken On Error: " + exception.toString());
                                }
                            });

                        }
                    });

        } catch (Exception e) {
            Log.e(TAG, "getSetFirebaseToken Exception: " + e.toString());
        }
    }

    private void setAppContext() {
        try {
            new D1Task.Builder().setContext(cordova.getActivity().getApplicationContext()).build();
        } catch (Exception e) {
            Log.e(TAG, "setAppContext : " + e.toString());
        }
    }

    private void configure(@NotNull final Context context,
                           @NotNull final Activity activity,
                           @NotNull final String serviceUrl,
                           @NotNull final String issuerId,
                           @NotNull final byte[] exponent,
                           @NotNull final byte[] modulus,
                           @NotNull final String digitalCardUrl,
                           @NotNull final String consumerId,
						   @NotNull String samsungServiceID,
                           @NotNull String visaClientAPPID) {

        try {
            mD1Task = new D1Task.Builder()
                    .setContext(context)
                    .setD1ServiceURL(serviceUrl)
                    .setIssuerID(issuerId)
                    .setD1ServiceRSAExponent(exponent)
                    .setD1ServiceRSAModulus(modulus)
                    .setDigitalCardURL(digitalCardUrl).build();

			if (samsungServiceID.isEmpty())samsungServiceID = null;
            if (visaClientAPPID.isEmpty())visaClientAPPID = null;
			
            final D1Params coreConfig = ConfigParams.buildConfigCore(consumerId);
            final D1Params cardConfig = ConfigParams.buildConfigCard(activity, OEMPayType.GOOGLE_PAY, samsungServiceID, visaClientAPPID);

            // D1Pay config.
            final D1PayConfigParams d1PayConfigParams = D1PayConfigParams.getInstance();
            d1PayConfigParams.setContactlessTransactionListener(mD1PayTransactionListener = new D1PayContactlessTransactionListener(cordova.getActivity().getApplicationContext(), null));
            d1PayConfigParams.setReplenishAuthenticationUIStrings("Replenishment Title",
                    "Replenishment Subtitle",
                    "Replenishment Description",
                    "Cancel");

            mD1Task.configure(new D1Task.ConfigCallback<Void>() {
                @Override
                public void onSuccess(final Void data) {
                    callback.success("D1 SDK Configuration Successfull");
                    Log.i(TAG, "D1 SDK Configuration Successfull");
                }

                @Override
                public void onError(@NonNull List<D1Exception> exceptions) {
                    for (final D1Exception exception : exceptions) {
						Log.e(TAG ,"D1 SDK Configuration onError : "+ exception.getMessage());
					}
					callback.error(createJsonError(exceptions));
                }
            }, coreConfig, cardConfig, d1PayConfigParams);
        } catch (Exception exception) {
            Log.e(TAG, "Configure Fun Error : " + exception.toString());
            callback.error("Configure Fun Error : " + exception.toString());
        }
    }

    private void login(@NotNull final byte[] issuerToken) {
        try {
            mD1Task.login(issuerToken, new D1Task.Callback<Void>() {
                @Override
                public void onSuccess(final Void data) {
                    callback.success("D1 SDK Login Successfull");
                    Log.i(TAG, "D1 SDK Login Successfull");
                }

                @Override
                public void onError(@NonNull final D1Exception exception) {
                    callback.error(createJsonError(Collections.singletonList(exception)));
                    Log.e(TAG, "D1 SDK Login Error : " + exception.toString());
                }
            });
        } catch (Exception e) {
            callback.error("Login : " + e.toString());
        }
    }

    public void checkCardDigitizationState(String cardID, CallbackContext callback) {
        try {
            D1Task.Callback<CardDigitizationState> digitizationCallback = new D1Task.Callback<CardDigitizationState>() {
                @Override
                public void onSuccess(@NonNull CardDigitizationState state) {
                    // update UI bases on the state value

                    // Hide button "Enable NFC Payment"
                    switch (state) {
                        case NOT_DIGITIZED:
                            // Check Device is Eligible
                            // show button "Enable NFC Payment"
                            callback.success("NOT_DIGITIZED");
                            Log.i(TAG, "Card Digitization State : NOT_DIGITIZED");
                            break;

                        case DIGITIZATION_IN_PROGRESS:
                            // Hide button "Enable NFC Payment"
                            // Show digitization in progress
                            callback.success("DIGITIZATION_IN_PROGRESS");
                            Log.i(TAG, "Card Digitization State : DIGITIZATION_IN_PROGRESS");
                            break;

                        case DIGITIZED:
                            // Tap&Pay settings
                            // check issuer application is the default payment application
                            //defaultPaymentApplication(context, callback);
                            callback.success("DIGITIZED");
                            Log.i(TAG, "Card Digitization State : DIGITIZED");
                            break;
                    }
                }

                @Override
                public void onError(@NonNull D1Exception exception) {
                    // Refer to D1 SDK Integration – Error Management section
                    // Exception: Not Supported: Display message 'Not Supported'
                    callback.error("DigitizationCallback : " + exception.toString());
                    Log.e(TAG, "Card Digitization State On Error : " + exception.toString());
                }
            };

            D1PayWallet d1PayWallet = mD1Task.getD1PayWallet();
            d1PayWallet.getCardDigitizationState(cardID, digitizationCallback);

        } catch (Exception e) {
            callback.error("CheckCardDigitizationState : " + e.toString());
        }
    }

    public void addDigitalCard(String cardID, CallbackContext callback) {
        try {
            Log.i(TAG, "AddDigitalCard CardID : " + cardID);
            currentCardID = cardID;
            D1PayWallet d1PayWallet = mD1Task.getD1PayWallet();

            d1PayWallet.registerD1PayDataChangedListener((cardId, state) -> {
                Log.i(TAG, "registerD1PayDataChangedListener : " + "cardId : " + cardId + " | state : " + state.toString());
                if (state == State.ACTIVE) {
                    // if it is the first digitization process, then:
                    // Tap and Pay settings - Check issuer application is the default payment application
                    // if not redirect end user to Tap and Pay settings
                    callback.success("Card Added Successfully");

                    //Store the Digital Card art locally
                    Log.i(TAG, "AddDigitalCard Current CardID : " + currentCardID);
                    storeCardMetaData(currentCardID);
                    fetchDefaultPaymentCard(currentCardID);


                }
            });


            D1Task.Callback<Void> digitalCardCallback = new D1Task.Callback<Void>() {
                @Override
                public void onSuccess(@Nullable Void ignored) {
                    // Data will be updated through Push Notification
                    Log.i(TAG, "Card Added Successfully");
                }

                @Override
                public void onError(@NonNull D1Exception exception) {
                    // Refer to D1 SDK Integration – Error Management section
                    callback.error("AddDigitalCard On Error " + exception.toString());
                    Log.e(TAG, "AddDigitalCard On Error : " + exception.toString());
                }
            };

            d1PayWallet.addDigitalCard(cardID, digitalCardCallback);

        } catch (Exception e) {
            callback.error("Add Digital Card Excep: " + e.toString());
        }
    }

    private void checkNFCStatus() {
        try {
            PackageManager pm = cordova.getContext().getPackageManager();
            if (pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) {
                callback.success("E"); //Eligible
                Log.i(TAG, "Device Eligible for NFC Payment");
            } else {
                callback.success("N"); //Not Eligible
                Log.i(TAG, "Device Not Eligible for NFC Payment");
            }
        } catch (Exception e) {
            callback.error("CheckNFCStatus : " + e.toString());
        }
    }

    private void checkDefaultPaymentApp() {
        try {
            CardEmulation cardEmulation = CardEmulation.getInstance(NfcAdapter.getDefaultAdapter(cordova.getContext()));

            // Construct the componentName with the default D1 Pay HCE service class
            ComponentName componentName = new ComponentName(cordova.getContext(), D1HCEService.class.getCanonicalName());

            if (cardEmulation.isDefaultServiceForCategory(componentName, CardEmulation.CATEGORY_PAYMENT)) {
                // Application is default NFC payment app
                callback.success("Default App");
                Log.i(TAG, "Default Payment App");
            } else {
                // Application is not default NFC payment app
                callback.success("Not Default App");
                Log.i(TAG, "Not Default Payment App");
            }
        } catch (Exception e) {
            callback.error("checkDefaultPaymentApp : " + e.toString());
        }
    }

    private void setDefaultPaymentApp() {
        try {
            // Construct the componentName with the default D1 Pay HCE service class
            ComponentName componentName = new ComponentName(cordova.getContext(), D1HCEService.class.getCanonicalName());

            Intent activate = new Intent();
            activate.setFlags(FLAG_ACTIVITY_NEW_TASK);
            activate.setAction(CardEmulation.ACTION_CHANGE_DEFAULT);
            activate.putExtra(CardEmulation.EXTRA_SERVICE_COMPONENT, componentName);
            activate.putExtra(CardEmulation.EXTRA_CATEGORY, CardEmulation.CATEGORY_PAYMENT);
            cordova.getContext().startActivity(activate);
        } catch (Exception e) {
            callback.error("setDefaultPaymentApp : " + e.toString());
        }
    }

    private String getDeviceName() {
        return Build.MANUFACTURER;
    }

    private String createJsonError(final List<D1Exception> exceptions) {
        try {
            final List<Map<String, Object>> json = new ArrayList<>();
            for (final D1Exception exception : exceptions) {
                final Map<String, Object> jsonMap = new HashMap<>();
                jsonMap.put("message", exception.getMessage());
				jsonMap.put("code", exception.getErrorCode());
                json.add(jsonMap);
            }

            if (json.size() > 1) {
                return new GsonBuilder().setPrettyPrinting().create().toJson(json);
            } else if (json.size() == 1) {
                return new GsonBuilder().setPrettyPrinting().create().toJson(json.get(0));
            }

            return new GsonBuilder().setPrettyPrinting().create().toJson(json);
        } catch (Exception e) {
            callback.error("CreateJsonError : " + e.toString());
        }
        return null;
    }

    private void replenishment(String cardIdToReplenish, boolean isForced) {
        /**
         * Specific for Visa, CVM might be required on replenishment
         * Hence, Application may need to check if it is on background or foreground
         */
        DeviceAuthenticationCallback cvmCallback = new DeviceAuthenticationCallback() {
            @Override
            public void onSuccess() {
                // User authentication is success
                Log.i(TAG, "cvmCallback onSuccess");
            }

            @Override
            public void onFailed() {
                // User authentication failed, the issuer app may ask end user to retry
                Log.i(TAG, "cvmCallback onFailed");
            }

            @Override
            public void onHelp(int fpCode, @NonNull CharSequence fpDetail) {
                // For BIOMETRIC only
                Log.i(TAG, "cvmCallback onHelp");
                // Issuer application may show the fpDetail message to the end user
            }

            @Override
            public void onError(int fpErrorCode) {
                // For BIOMETRIC only
                Log.i(TAG, "cvmCallback onError " + fpErrorCode);
                // Error happened while doing BIOMETRIC authenticate (e.g using wrong finger too many times and the sensor is locked)
                // Depending on the fpErrorCode, the issuer application should troubleshoot the end user.
            }
        };

        if (cardIdToReplenish != null) {
            D1PayWallet d1PayWallet = mD1Task.getD1PayWallet();
            d1PayWallet.replenish(cardIdToReplenish, isForced, cvmCallback,
                    new D1Task.Callback<Void>() {
                        @Override
                        public void onSuccess(Void ignored) {
                            // replenishment completed
                            Log.i(TAG, "replenish onSuccess ");
                        }

                        @Override
                        public void onError(@NonNull D1Exception exception) {
                            //Refer to D1 SDK Integration – Error Management section
                            Log.i(TAG, "replenish onError " + exception.toString());
                        }
                    });
        }
    }

    private void getCardData(String cardID){
        try {
            D1Task.Callback<CardMetadata> getCardDataCallback = new D1Task.Callback<CardMetadata>() {
                @Override
                public void onSuccess(CardMetadata cardMetadata) {
                    String last4FPAN = cardMetadata.getLast4Pan();
                    String fPANExpDate = cardMetadata.getExpiryDate();

                    callback.success(last4FPAN+","+fPANExpDate);
                }

                @Override
                public void onError(@NonNull D1Exception e) {
                    callback.error(e.toString());
                }
            };
            mD1Task.getCardMetadata(cardID,getCardDataCallback);
        }catch (Exception e){
            callback.error(e.toString());
            Log.e(TAG, "getCardMetaData onError : " + e.toString());
        }

    }

    private void getDigitalCardList() {
        try {
            JSONArray cardDetailsArray = new JSONArray();
            mD1Task.getD1PayWallet().getDigitalCardList(new D1Task.Callback<Map<String, D1PayDigitalCard>>() {
                @Override
                public void onSuccess(Map<String, D1PayDigitalCard> digitalCards) {
                    for (Map.Entry<String, D1PayDigitalCard> entry : digitalCards.entrySet()) {
                        try {
                            String d1CardID = entry.getKey();
                            D1PayDigitalCard digitalCard = entry.getValue();
                            String filePath = CoreUtils.getInstance().getFilePath(cordova.getActivity().getApplicationContext(), d1CardID);

                            JSONObject cardObj = new JSONObject();
                            cardObj.put("CardArt", filePath);
                            cardObj.put("CardID", d1CardID);
							cardObj.put("DigitalCardID",digitalCard.getCardID());
                            cardObj.put("Last4Pan", digitalCard.getLast4());
                            cardObj.put("ExpiryDate", digitalCard.getExpiryDate());
                            cardObj.put("IsDefaultCard", digitalCard.isDefaultCard());
                            cardObj.put("NumberOfPaymentsLeft", digitalCard.getNumberOfPaymentsLeft());
                            cardObj.put("Scheme", digitalCard.getScheme());
                            cardObj.put("State", digitalCard.getState());
                            cardObj.put("TncURL", digitalCard.getTncURL());
                            cardObj.put("IsAuthRequiredBeforeReplenishment", digitalCard.isAuthenticationRequiredBeforeReplenishment());
                            cardObj.put("IsODAReplenishmentNeeded", digitalCard.isODAReplenishmentNeeded());
                            cardObj.put("IsReplenishmentNeeded", digitalCard.isReplenishmentNeeded());

                            if (digitalCard.getNumberOfPaymentsLeft() < 5) {
                                replenishment(d1CardID, true);
                            }
                            cardDetailsArray.put(cardObj);

                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    Log.i(TAG, "Card Details : " + cardDetailsArray.toString());
                    callback.success(cardDetailsArray.toString());
                }

                @Override
                public void onError(@NonNull D1Exception e) {
                    Log.e(TAG, "getDigitalCardList onError : " + e.toString());
                }
            });


        } catch (Exception e) {
            Log.e(TAG, "getDigitalCardList Exception : " + e.toString());
        }
    }

    private void storeCardMetaData(String cardID) {
        try {
            Log.i(TAG, "storeCardMetaData :  " + cardID);
            D1Task.Callback<CardMetadata> cardMetadataCallback = new D1Task.Callback<CardMetadata>() {
                @Override
                public void onSuccess(CardMetadata cardMetadata) {
                    D1Task.Callback<List<CardAsset>> listCallback = new D1Task.Callback<List<CardAsset>>() {
                        @Override
                        public void onSuccess(List<CardAsset> cardAssets) {
                            for (final CardAsset cardAsset : cardAssets) {
                                for (final AssetContent assetContent : cardAsset.getContents()) {
                                    final byte[] data = Base64.decode(assetContent.getEncodedData(), Base64.DEFAULT);
                                    Log.i(TAG, "Card Image Length :  " + data.length);
                                    CoreUtils.getInstance().writeToFile(cordova.getActivity().getApplicationContext(), cardID, data);
                                    break;
                                }
                                break;
                            }
                        }

                        @Override
                        public void onError(@NonNull D1Exception e) {
                            // Handles the error. For example, log it, display it, and so on.
                            Log.e(TAG, "getAssetList On Error : " + e.toString());
                        }
                    };
                    cardMetadata.getAssetList(listCallback);
                }

                @Override
                public void onError(D1Exception exception) {
                    // Refer to D1 SDK Integration – Error Management section.
                    Log.e(TAG, "getCardMetaData onError : " + exception.toString());
                }
            };
            mD1Task.getCardMetadata(cardID, cardMetadataCallback);
        } catch (Exception e) {
            Log.e(TAG, "getCardData Exception : " + e.toString());
        }
    }

    private void setDefultPaymentCard(String d1CardID) {
        try {
            D1PayWallet d1PayWallet = mD1Task.getD1PayWallet();
            d1PayWallet.setDefaultPaymentDigitalCard(
                    d1CardID,
                    new D1Task.Callback<Void>() {
                        @Override
                        public void onSuccess(Void ignored) {
                            callback.success("success");
                            Log.i(TAG, "setDefultPaymentCard "+d1CardID+" : success");
                            storeDefaultCardDetails(d1CardID);
                        }

                        @Override
                        public void onError(@NonNull D1Exception exception) {
                            // Refer to D1 SDK Integration – Error Management section
                            // Common error: invalid d1CardID
                            callback.error(exception.toString());
                            Log.e(TAG, "setDefultPaymentCard onError : " + exception.toString());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "setDefultPaymentCard Exception : " + e.toString());
        }
    }

    private void unSetDefaultPaymentCard() {
        try {
            D1PayWallet d1PayWallet = mD1Task.getD1PayWallet();
            d1PayWallet.unsetDefaultPaymentDigitalCard(
                    new D1Task.Callback<Void>() {
                        @Override
                        public void onSuccess(Void ignored) {
                            // the default card is unset
                            callback.success("success");
                            Log.i(TAG, "unSetDefaultPaymentCard : success");
                        }

                        @Override
                        public void onError(@NonNull D1Exception exception) {
                            // Refer to D1 SDK Integration – Error Management section
                            // Common error: there is no default card.
                            callback.error(exception.toString());
                            Log.e(TAG, "unSetDefaultPaymentCard onError : " + exception.toString());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "unSetDefaultPaymentCard Exception : " + e.toString());
        }
    }

    private void fetchDefaultPaymentCard(String cardId) {
        D1PayWallet d1PayWallet = mD1Task.getD1PayWallet();

        d1PayWallet.getDefaultPaymentDigitalCard(new D1Task.Callback<String>() {
            @Override
            public void onSuccess(@Nullable String defaultCardId) {
                if (defaultCardId != null) {
                    Log.i(TAG, "Default Payment Card ID: " + defaultCardId);

                    // Compare the fetched card ID with the provided one
                    if (defaultCardId.equalsIgnoreCase(cardId)) {
                        storeDefaultCardDetails(cardId);
                    }
                }else{
                    Log.i(TAG, "Default cardid not found");
                }
            }

            @Override
            public void onError(@NonNull D1Exception exception) {
                // Log error when there's an issue fetching the default payment card
                Log.e(TAG, "Error fetching default payment card: " + exception.toString());
            }
        });
    }
	
	private void storeDefaultCardDetails(String cardId) {
        try {
            // Get SharedPreferences instance
            SharedPreferences sharedPreferences = cordova.getActivity().getSharedPreferences("AHLIBANK", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();

            // Define callback for getting card metadata
            D1Task.Callback<CardMetadata> cardDataCallback = new D1Task.Callback<CardMetadata>() {
                @Override
                public void onSuccess(CardMetadata cardMetadata) {
                    if (cardMetadata != null) {
                        // Get the last 4 digits of the card number and expiry date
                        String last4FPAN = cardMetadata.getLast4Pan();
                        String fpanExpDate = cardMetadata.getExpiryDate();

                        // Store the card details in SharedPreferences
                        editor.putString("CARDID", cardId);
                        editor.putString("LAST4FPAN", last4FPAN);
                        editor.putString("EXPDATE", fpanExpDate);
                        editor.apply();

                        Log.i(TAG, "Card details successfully stored.");
                    } else {
                        Log.w(TAG, "Card metadata is null.");
                    }
                }

                @Override
                public void onError(@NonNull D1Exception e) {
                    // Log error when retrieving card metadata fails
                    Log.e(TAG, "Error retrieving card metadata: " + e.toString());
                }
            };

            // Fetch card metadata
            mD1Task.getCardMetadata(cardId, cardDataCallback);
        } catch (Exception e) {
            // Catch unexpected exceptions and log them
            Log.e(TAG, "Unexpected error in storeDefaultCardDetails: " + e.toString());
        }
    }


	private void initiateRemoveCard(String cardID){
        try{
            Log.i(TAG,"Initiate Remove Digital Card : "+cardID);
            D1PayWallet d1PayWallet = mD1Task.getD1PayWallet();
            d1PayWallet.getDigitalCard(cardID,
                    new D1Task.Callback<D1PayDigitalCard>() {
                        @Override
                        public void onSuccess(@NonNull D1PayDigitalCard digitalCard) {
                            Log.i("GetDigitalCard  : ", "On Success");
                            removeDigitalCard(cardID,digitalCard);
                        }

                        @Override
                        public void onError(@NonNull D1Exception exception) {
                            Log.e(TAG, "GetDigitalCard onError : " + exception.toString());
                        }
                    }
            );
        }catch (Exception e){
            Log.e(TAG, "InitiateRemoveCard Exception : " + e.toString());
        }
    }

    private void removeDigitalCard(String d1CardID, D1PayDigitalCard digitalCard) {
        try {
            D1PayWallet d1PayWallet = mD1Task.getD1PayWallet();
            d1PayWallet.registerD1PayDataChangedListener((cardId, state) -> {
                // The actual update to wallet is received here
                Log.i(TAG,"registerD1PayDataChangedListener ===> Card ID : "+cardId+" | State : "+state);

                if (state == State.DELETED){
                    Log.i(TAG,"registerD1PayDataChangedListener : Deleted successfully");
                    callback.success("Deleted successfully");
                }

            });

            d1PayWallet.updateDigitalCard(
                    d1CardID,
                    digitalCard,
                    CardAction.DELETE,
                    new D1Task.Callback<Boolean>() {
                        @Override
                        public void onSuccess(@NonNull Boolean result) {
                            Log.i("RemoveDigitalCard  : ", "On Success : "+result);
                        }

                        @Override
                        public void onError(@NonNull D1Exception exception) {
                            // Refer to D1 SDK Integration – Error Management section
                            Log.e(TAG, "RemoveDigitalCard onError : " + exception.toString());
                        }
                    }
            );
        } catch (Exception e) {
            Log.e(TAG, "RemoveDigitalCard Exception : " + e.toString());
        }
    }
 


    private void doManualPayment(String cardID) {
        try {
			
            // D1Pay configuration : register contactless transaction callback
            Log.i(TAG, "doManualPayment Card ID : " + cardID);
			mD1PayTransactionListener.deactivate();
			D1PayConfigParams.getInstance().setManualModeContactlessTransactionListener(mD1PayTransactionListener = new D1PayContactlessTransactionListener(cordova.getActivity().getApplicationContext(), cardID));
			mD1Task.getD1PayWallet().startManualModePayment(cardID);
			
        } catch (Exception e) {
            Log.e(TAG, "doManualPayment Exception : " + e.toString());
        }
    }
	
	public void getD1PayTxnHistory(String cardID){
        try{
            mD1Task.getD1PayWallet().getTransactionHistory(cardID, new D1Task.Callback<TransactionHistory>() {
                @Override
                public void onSuccess(TransactionHistory transactionHistory) {
                    Log.i(TAG,"D1PayTXNHistory : "+transactionHistory.getRecords());
                    callback.success(String.valueOf(transactionHistory.getRecords()));
                }

                @Override
                public void onError(@NonNull D1Exception e) {
                    Log.e(TAG,"GetD1PayTxnHistory OnError : "+e.toString());
                    callback.error(e.toString());
                }
            });

        }catch (Exception e){
            Log.e(TAG,"GetD1PayTxnHistory Exception : "+e.toString());
        }
    }
	
	public D1Task getD1Task(){
        if (mD1Task == null){
            final String errDesc = "Need to configure D1 SDK first.";
            throw new IllegalStateException(errDesc);
        }
        return mD1Task;
    }

    public class D1PayContactlessTransactionListener extends ContactlessTransactionListener {
        public Context mContext;
        private double mAmount;
        private String mCurrency;
        private final String mCardId;

        /**
         * Creates a new instance of {@code D1PayContactlessTransactionListener}.
         *
         * @param context Context.
         * @param cardId  CardId
         */
        public D1PayContactlessTransactionListener(@NonNull final Context context, final String cardId) {
            super();
            mContext = context;
            mCardId = cardId;

            resetState();
        }

        @Override
        public void onTransactionStarted() {
            // Display transaction is ongoing
            Log.i(TAG, "onTransactionStarted");
            updateState(PaymentState.STATE_ON_TRANSACTION_STARTED, new PaymentData(mAmount, mCurrency, mCardId));
        }

        @Override
        public void onAuthenticationRequired(@NonNull final VerificationMethod method) {
            /* Only applicable for 2-TAP experience
             * Display transaction details and tell consumer to authenticate
             */
            Log.i(TAG, "onAuthenticationRequired");

            // All current state values are no longer relevant.
            resetState();

            updateAmountAndCurrency();

            // Update state and notify everyone.
            updateState(PaymentState.STATE_ON_AUTHENTICATION_REQUIRED, new PaymentData(mAmount, mCurrency, mCardId));
        }

        @Override
        public void onReadyToTap() {
            /* Only applicable for 2-TAP experience
             * Inform customer application is ready for 2nd TAP.
             * Display transaction details and display the remaining time for the 2nd TAP
             */
            Log.i(TAG, "onReadyToTap");

            // Register the timeout callback to update the user on remaining time for the 2nd tap.
            this.registerDeviceAuthTimeoutCallback(new DeviceAuthenticationTimeoutCallback() {
                @Override
                public void onTimer(final int remain) {
                    // The mobile application should update the countdown screen with current "remaining" time.
                }

                @Override
                public void onTimeout() {
                    // The mobile application should inform end user of the timeout error.
                    updateAmountAndCurrency();
                    deactivate();
                    updateState(PaymentState.STATE_ON_ERROR, new PaymentErrorData(null, "Timer exceeded", mAmount, mCurrency, mCardId));
                    //updateState(PaymentState.STATE_ON_ERROR, new PaymentErrorData(null, mContext.getString(com.thalesgroup.d1.core.R.string.transaction_timeout), mAmount, mCurrency, mCardId));
                }
            });

            updateState(PaymentState.STATE_ON_READY_TO_TAP, new PaymentData(mAmount, mCurrency, mCardId));
        }

        @Override
        public void onTransactionCompleted() {
            /* The transaction has been completed successfully on the mobile app.
             * Display transaction status success and details
             */
            try {
                updateAmountAndCurrency();
                updateState(PaymentState.STATE_ON_TRANSACTION_COMPLETED, new PaymentData(mAmount, mCurrency, mCardId));

                Log.i(TAG, "onTransactionCompleted Card ID : " + mCardId);

                CoreUtils.getInstance().runInMainThread(() -> {
                    final Intent intent = new Intent(mContext, TransactionSent.class);
                    intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra("HeaderName", "Ahli Pay");
                    if (mCardId == null) {
                        intent.putExtra("CardID", "");
                    } else {
                        intent.putExtra("CardID", mCardId);
                    }

                    mContext.startActivity(intent);
                });

            } catch (Exception e) {
                Log.e(TAG, "onTransactionCompleted Exception : " + e.toString());
            }
        }

        @Override
        public void onError(@NonNull final D1Exception error) {
            /* The transaction failed due to an error.
             * Mobile application should get detailed information from the "error" param and inform the end user.
             */
            Log.e(TAG, "onError : " + error.toString());
            // All current state values are no longer relevant.
            resetState();

            updateState(PaymentState.STATE_ON_ERROR,
                    new PaymentErrorData(error.getErrorCode(), error.getLocalizedMessage(), mAmount, mCurrency, mCardId));
        }

        /**
         * Updates the amount and currency and wipes the transaction data.
         */
        private void updateAmountAndCurrency() {
            Log.i(TAG, "updateAmountAndCurrency");
            final TransactionData transactionData = getTransactionData();
            if (transactionData == null) {
                mAmount = -1.0;
                mCurrency = null;
            } else {
                mAmount = getTransactionData().getAmount();
                mCurrency = "OMR";
            }

            if (transactionData != null) {
                transactionData.wipe();
            }
        }

        /**
         * Resets the amount, currency and payment state.
         */
        private void resetState() {
            Log.i(TAG, "resetState");
            mAmount = 0.0;
            mCurrency = null;
        }

        /**
         * Updates the payment state and payment data.
         *
         * @param state Payment state.
         * @param data  Payment data.
         */
        protected void updateState(final PaymentState state, final PaymentData data) {
            // Store last state so it can be read onResume when app was not in foreground.
            Log.i(TAG, "updateState : " + state);
            // Notify rest of the application in UI thread.
            CoreUtils.getInstance().runInMainThread(() -> {
                final Intent intent = new Intent(mContext, cordova.getContext().getClass());
                intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("STATE_EXTRA_KEY", state);
                intent.putExtra("PAYMENT_DATA_EXTRA_KEY", data);
                mContext.startActivity(intent);
            });
            if (state == PaymentState.STATE_ON_AUTHENTICATION_REQUIRED) {
                doAuthenticate();
            }
        }

        private final DeviceAuthenticationCallback deviceAuthenticationCallback = new DeviceAuthenticationCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onFailed() {
                callback.success("Authentication Failed");
            }

            @Override
            public void onError(int i) {
            }

            @Override
            public void onHelp(int i, @NonNull CharSequence charSequence) {
            }
        };


        private void doAuthenticate() {
            try {
                Log.i(TAG, "doAuthenticate Called");
                final AuthenticationParameter authenticationParameter = new AuthenticationParameter(cordova.getActivity(),
                        "Authentication Required",
                        "Please authenticate yourself for payment",
                        "See amount on POS",
                        "Cancel",
                        deviceAuthenticationCallback);

                mD1PayTransactionListener.startAuthenticate(authenticationParameter);
            } catch (Exception e) {
                Log.e(TAG, "doAuthenticate : " + e.toString());
            }
        }
    }

    public void checkD1PushCardDigitizationStateSamsungPay(String cardId){
        try{
            OEMPayType wallet = OEMPayType.SAMSUNG_PAY;
            D1PushWallet d1PushWallet = mD1Task.getD1PushWallet();
            d1PushWallet.getCardDigitizationState(cardId, wallet, new D1Task.Callback<CardDigitizationState>() {
                @Override
                public void onSuccess(CardDigitizationState cardDigitizationState) {
                    switch (cardDigitizationState) {
                        case NOT_DIGITIZED:
                            // show button "Add to Google/Samsung Pay"
                            callback.success("NOT_DIGITIZED");
                            Log.i(TAG,"Card Digitization State : NOT_DIGITIZED");
                            break;

                        case PENDING_IDV:
                            // 1. show button "Activate your card"
                            // 2. Authenticate the end user
                            // 3. Perform activation: d1PushWallet.activateDigitalCard(cardID, wallet, callback)
                            callback.success("PENDING_IDV");
                            Log.i(TAG,"Card Digitization State : PENDING_IDV");
                            break;

                        case DIGITIZED:
                            // hide button "Add to Google/Samsung Pay"
                            callback.success("DIGITIZED");
                            Log.i(TAG,"Card Digitization State : DIGITIZED");
                            break;

                        default:
                            // do nothing
                            callback.success("NULL");
                            Log.i(TAG,"Card Digitization State : NULL");
                            break;
                    }
                }

                @Override
                public void onError(@NonNull D1Exception e) {
                    callback.error(e.toString());
                    Log.e(TAG,"checkD1PushCardDigitizationState Exception : "+e.toString());
                }
            });


        }catch(Exception e){
            Log.e(TAG,"checkD1PushCardDigitizationState Exception : "+e.toString());
        }
    }

    public void d1PushActivateDigitalCardSamsungPay(){
        try{
            D1PushWallet d1PushWallet = mD1Task.getD1PushWallet();
            d1PushWallet.activateSamsungPay();
        }catch (Exception e){
            Log.e(TAG, "d1PushActivateDigitalCard Exception : "+e.toString());
        }
    }

    public void d1PushAddDigitalCardToSamsungPay(String cardId){
        try{
            D1PushWallet d1PushWallet = mD1Task.getD1PushWallet();
            OEMPayType wallet = OEMPayType.SAMSUNG_PAY;

            d1PushWallet.addDigitalCardToOEM(cardId, wallet, cordova.getActivity(), new D1Task.Callback<Object>() {
                @Override
                public void onSuccess(Object o) {
                    callback.success("success");
                }

                @Override
                public void onError(@NonNull D1Exception e) {
                    callback.success(e.toString());
                }
            });
        }catch (Exception e){
            Log.e(TAG, "d1PushActivateDigitalCard Exception : "+e.toString());
        }
    }
	
	public void checkD1PushCardDigitizationStateGPay(String cardId){
        try{
            OEMPayType wallet = OEMPayType.GOOGLE_PAY;
            D1PushWallet d1PushWallet = mD1Task.getD1PushWallet();
            d1PushWallet.getCardDigitizationState(cardId, wallet, new D1Task.Callback<CardDigitizationState>() {
                @Override
                public void onSuccess(CardDigitizationState cardDigitizationState) {
                    switch (cardDigitizationState) {
                        case NOT_DIGITIZED:
                            // show button "Add to Google/Samsung Pay"
                            callback.success("NOT_DIGITIZED");
                            Log.i(TAG, "Card Digitization State : NOT_DIGITIZED");
                            break;

                        case PENDING_IDV:
                            // 1. show button "Activate your card"
                            // 2. Authenticate the end user
                            // 3. Perform activation: d1PushWallet.activateDigitalCard(cardID, wallet, callback)
                            callback.success("PENDING_IDV");
                            Log.i(TAG, "Card Digitization State : PENDING_IDV");
                            break;

                        case DIGITIZED:
                            // hide button "Add to Google/Samsung Pay"
                            callback.success("DIGITIZED");
                            Log.i(TAG, "Card Digitization State : DIGITIZED");
                            break;

                        default:
                            // do nothing
                            callback.success("NULL");
                            Log.i(TAG, "Card Digitization State : NULL");
                            break;
                    }
                }

                @Override
                public void onError(@NonNull D1Exception e) {
                    callback.error(e.toString());
                    Log.e(TAG, "checkD1PushCardDigitizationStateGPay Exception : " + e.toString());
                }
            });
        }catch (Exception e){
            Log.e(TAG,"checkD1PushCardDigitizationStateGPay Exception : "+e.toString());
        }
    }
    
    public void addCardToGoogleWallet(String cardId){
        try{
            Log.i(TAG,"addCardToGoogleWallet Card Id : "+cardId);

            OEMPayType wallet = OEMPayType.GOOGLE_PAY;
            D1Task.Callback<Object> gPayCallback = new D1Task.Callback<Object>() {
                @Override
                public void onSuccess(Object data) {
                    callback.success("success");
                    Log.i(TAG, "Add Card To GoogleWallet Success");
                }

                @Override
                public void onError(@NonNull D1Exception exception) {
                    // Handles the error.
                    Log.e(TAG, "Add Card To GoogleWallet Error : "+exception.getMessage());
                    callback.error(exception.getMessage());
                }
            };
            D1PushWallet d1PushWallet = mD1Task.getD1PushWallet();
            d1PushWallet.addDigitalCardToOEM(cardId, wallet, cordova.getActivity(), gPayCallback);


        }catch(Exception e){
            Log.e(TAG,"addCardToGoogleWallet Excpetion : "+e.toString());
        }
    }

    // Activity Result: The `onActivityResult` method is received in the issuer app from GPay SDK, and passed back to D1 SDK to handle the result.
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		Log.i(TAG, "Gpay OnActivityResult requestCode : "+requestCode+ " | resultCode : "+resultCode+" | data : "+data.getDataString());
        mD1Task.handleCardResult(requestCode, resultCode, data);
    }
	
	 
}
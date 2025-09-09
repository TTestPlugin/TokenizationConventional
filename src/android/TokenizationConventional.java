package com.ahlibank.tokenization;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.gson.GsonBuilder;
import com.huawei.hms.api.ConnectionResult;
import com.huawei.hms.api.HuaweiApiAvailability;
import com.thalesgroup.gemalto.d1.ConfigParams;
import com.thalesgroup.gemalto.d1.D1Exception;
import com.thalesgroup.gemalto.d1.D1Params;
import com.thalesgroup.gemalto.d1.D1Task;
import com.thalesgroup.gemalto.d1.card.CardDigitizationState;
import com.thalesgroup.gemalto.d1.card.D1PushWallet;
import com.thalesgroup.gemalto.d1.card.OEMPayType;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class echoes a string called from JavaScript.
 */
public class TokenizationConventional extends CordovaPlugin {

    public D1Task mD1Task;
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
                    //pushToken(huaweiAppID);
                    break;

                case "login":
                    final String token = args.getString(0);
                    login(token.getBytes(StandardCharsets.UTF_8));
                    break;

                case "checkNFCEnable":
                    checkNFCStatus();
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
            }, coreConfig, cardConfig);
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
	
	public D1Task getD1Task(){
        if (mD1Task == null){
            final String errDesc = "Need to configure D1 SDK first.";
            throw new IllegalStateException(errDesc);
        }
        return mD1Task;
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
package com.ahlibank.tokenization;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.huawei.hms.push.HmsMessageService;
import com.huawei.hms.push.RemoteMessage;
import com.thalesgroup.gemalto.d1.D1Exception;
import com.thalesgroup.gemalto.d1.D1Task;
import com.thalesgroup.gemalto.d1.PushResponseKey;

import java.util.Map;

/**
 * Custom Huawei Push Notification Service for D1 tokenization integration.
 * Handles push token updates and message processing using D1 SDK.
 */
public class D1HmsMessageService extends HmsMessageService {

    private static final String TAG = "Outsystems==> Huawei : ";
    private D1Task d1Task;

    @Override
    public void onNewToken(@NonNull String token) {
        Log.i(TAG, " Push Token received: " + "HMS:"+token);

        // Initialize D1Task if not already initialized
        if (d1Task == null) {
            d1Task = new D1Task.Builder()
                    .setContext(getApplicationContext())
                    .build();
        }

        // Update the push token with the D1 SDK
        d1Task.updatePushToken("HMS:"+token, new D1Task.Callback<Void>() {
            @Override
            public void onSuccess(@Nullable Void ignored) {
                Log.i(TAG, "updatePushToken success: " + "HMS:"+token);
            }

            @Override
            public void onError(@NonNull D1Exception exception) {
                Log.e(TAG, "updatePushToken error: ", exception);
            }
        });
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        final Map<String, String> dataMap = remoteMessage.getDataOfMap();
        Log.i(TAG, "onMessageReceived: " + dataMap);

        // Ensure D1Task is initialized
        if (d1Task == null) {
            d1Task = new D1Task.Builder().setContext(getApplicationContext()).build();
			Log.i(TAG, "d1Task initialized");
        }

        // Avoid ANR: move heavy processing off main thread
		new Thread(() -> {
			try {
				Log.i(TAG, "processNotification initialized");
				d1Task.processNotification(remoteMessage.getDataOfMap(), new D1Task.Callback<Map<PushResponseKey, String>>() {
					@Override
					public void onSuccess(Map<PushResponseKey, String> result) {
						Log.i(TAG, "ProcessNotification success: " + result);
					}

					@Override
					public void onError(@NonNull D1Exception e) {
						Log.e(TAG, "ProcessNotification error: " + e);
					}
				});
			} catch (Exception ex) {
				Log.e(TAG, "Exception in processNotification: " + ex);
			}
		}).start();
    }
}

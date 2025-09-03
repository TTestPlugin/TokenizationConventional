package com.ahlibank.tokenization;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.thalesgroup.gemalto.d1.D1Exception;
import com.thalesgroup.gemalto.d1.D1Task;
import com.thalesgroup.gemalto.d1.card.CardMetadata;

import java.nio.charset.StandardCharsets;

public class TransactionSent extends Activity {

    private D1Task d1Task = null;
    private static final String TAG = "Outsystems==>" + CoreUtils.class.getSimpleName();

    private TextView txtViewCardNum;
    private TextView txtViewExpDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
        		
			// Initialize D1Task if it hasn't been initialized yet
            if (d1Task == null) {
                d1Task = new D1Task.Builder().setContext(getApplicationContext()).build();
            }

            // Set the layout dynamically based on the current package
            String packageName = getApplication().getPackageName();
            setContentView(getApplication().getResources().getIdentifier("transaction_sent_drop2", "layout", packageName));

            // Initialize UI components
            TextView headerName = findViewById(getResourceId("headerName", "id"));
            ImageView imageView = findViewById(getResourceId("cardImage", "id"));
            Button backButton = findViewById(getResourceId("backBtn", "id"));
            txtViewCardNum = findViewById(getResourceId("cardNum", "id"));
            txtViewExpDate = findViewById(getResourceId("expDate", "id"));

            // Set up back button listener
            backButton.setOnClickListener(v -> finish());

            // Get the data from the intent
            Intent intent = getIntent();
            String headerNameText = intent.getStringExtra("HeaderName");
            if (headerNameText != null) {
                headerName.setText(headerNameText);
            }

            String cardID = intent.getStringExtra("CardID");
            SharedPreferences sharedPreferences = getSharedPreferences("AHLIBANK", Context.MODE_PRIVATE);

            // Handle cardID and SharedPreferences fallback
            if (cardID == null || cardID.trim().isEmpty()) {
                cardID = sharedPreferences.getString("CARDID", null);
                if (cardID != null) {
                    Log.i(TAG, "Contactless transaction without opening app, CARD ID: " + cardID);
                } else {
                    Log.e(TAG, "CardID is null or empty and not found in SharedPreferences");
                    return;  // Exit if no valid cardID is available
                }
            } else {
                Log.i(TAG, "Contactless transaction using mobile app, CARD ID: " + cardID);
            }

            // Read card data from file and display
            byte[] cardData = CoreUtils.getInstance().readFromFile(getApplicationContext(), cardID);
            if (cardData == null || cardData.length == 0) {
                Log.e(TAG, "Failed to read data for CardID: " + cardID + ". Data is null or empty.");
                return;
            }

            Bitmap bitmap = BitmapFactory.decodeByteArray(cardData, 0, cardData.length);
            if (bitmap != null) {
                Log.i(TAG, "Bitmap received successfully");
                imageView.setImageBitmap(bitmap);
            } else {
                Log.e(TAG, "Failed to decode bitmap for CardID: " + cardID);
            }

            // If the CardID is empty, use data from SharedPreferences
            if (cardID.isEmpty()) {
                String fPAN = "**** **** **** " + sharedPreferences.getString("LAST4FPAN", "");
                String expDate = sharedPreferences.getString("EXPDATE", "");
                String formattedDate = formatExpirationDate(expDate);
                txtViewCardNum.setText(fPAN);
                txtViewExpDate.setText(formattedDate);
                return;
            }

            // If CardID is valid, fetch tokenized card data
            if (!cardID.isEmpty()) {
                Log.i(TAG, "Fetching tokenized card details for CardID: " + cardID);
                fetchCardData(cardID);
            } else {
                Log.e(TAG, "CardID is invalid; skipping tokenized card detail retrieval");
            }
        } catch (Exception e) {
            Log.e(TAG, "TransactionSent On Error: " + e.toString());
        }
    }

        // Helper method to get resource ID
    private int getResourceId(String name, String type) {
        return getResources().getIdentifier(name, type, getPackageName());
    }

    // Helper method to format expiration date
    private String formatExpirationDate(String expDate) {
        return String.format("%s/%s", expDate.substring(0, 2), expDate.substring(2, 4));
    }

    // Fetch and display tokenized card data
    private void fetchCardData(String cardID) {
        try {
            Log.i(TAG, "Fetching card metadata for CardID: " + cardID);

            TokenizationConventional tokenizationConventional = TokenizationConventional.getInstance();
            d1Task = tokenizationConventional.getD1Task();

            D1Task.Callback<CardMetadata> cardDataCallback = new D1Task.Callback<CardMetadata>() {
                @Override
                public void onSuccess(CardMetadata cardMetadata) {
                    String last4FPAN = cardMetadata.getLast4Pan();
                    String expDate = cardMetadata.getExpiryDate();
                    String formattedDate = formatExpirationDate(expDate);

                    Log.i(TAG, "Card metadata fetched successfully. Last 4 FPAN: " + last4FPAN + " | Expiration Date: " + formattedDate);
                    txtViewCardNum.setText("**** **** **** " + last4FPAN);
                    txtViewExpDate.setText(formattedDate);
                }

                @Override
                public void onError(@NonNull D1Exception e) {
                    Log.e(TAG, "Error fetching card metadata: " + e.toString(), e);
                }
            };

            d1Task.getCardMetadata(cardID, cardDataCallback);
        } catch (Exception e) {
            Log.e(TAG, "Error fetching card metadata: " + e.toString(), e);
        }
    }
}
package com.ahlibank.tokenization;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * CoreUtils container holding generic helper functions.
 */
final public class CoreUtils {

    private static final String TAG = "Outsystems==>"+CoreUtils.class.getSimpleName();

    private static final CoreUtils INSTANCE;

    private CoreUtils() {
        // private constructor
    }

    static {
        INSTANCE = new CoreUtils();
    }

    /**
     * Return singleton of public API for application use.
     *
     * @return {@code CoreUtils}.
     */
    public static CoreUtils getInstance() {
        return INSTANCE;
    }

    /**
     * @param runnable Runnable.
     */
    public void runInMainThread(final Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }

    /**
     * @param context  Context.
     * @param fileName String.
     * @param data     {@code ByteArray}.
     */
    public void writeToFile(final Context context, final String fileName, final byte[] data) {

        try (FileOutputStream outputStream = context.openFileOutput(fileName, Context.MODE_PRIVATE)) {
            outputStream.write(data);
            Log.i(TAG, "WriteToFile : "+fileName);
        } catch (final IOException exception) {
            if (exception.getMessage() != null) {
                Log.e(TAG, exception.getMessage());
            } else {
                Log.d(TAG, "CoreUtils - writeToFile Error");
            }
        }
    }

    /**
     * @param context  Context.
     * @param fileName String.
     *
     * @return {@code ByteArray}.
     */
    public byte[] readFromFile(final Context context, final String fileName) {
        final byte[] buffer = new byte[1024];
        try (FileInputStream inputStream = context.openFileInput(fileName)) {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            int bytesRead = inputStream.read(buffer);
            while (bytesRead != -1) {
                outputStream.write(buffer, 0, bytesRead);
                bytesRead = inputStream.read(buffer);
            }

            return outputStream.toByteArray();
        } catch (final IOException exception) {
            // nothing to do
        }
        // nothing to do

        return new byte[0];
    }

    public String getFilePath(Context context,  String fileName){

        File file = new File(context.getFilesDir(), fileName);

        return "file://" + file.getAbsolutePath();
    }

    public void showToastMsg(Activity activity,String message){
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }
}

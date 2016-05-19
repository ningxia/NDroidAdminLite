package edu.nd.nxia.cimonlite.database;

//import android.util.Log;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.nfc.Tag;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import com.crashlytics.android.Crashlytics;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import edu.nd.nxia.cimonlite.MyApplication;

/**
 * Communication facility for uploading to server
 *
 * @author Xiao(Sean) Bo
 *
 */
public class DataCommunicator {
    private URL url;
    //private String url_c = "http://129.74.247.90:9003/Update_Data/";
    private String url_c = "http://m-health.cse.nd.edu:8100/Update_Data/";
    private String TAG = "DataCommunicator";
    private HttpURLConnection connection = null;

    public DataCommunicator() throws MalformedURLException{
        this.url = new URL(url_c);
    }

    /**
     * Convert inputStream to regular string
     *
     * @param is             Input stream acquired from server
     *
     * @author Xiao(Sean Bo)
     *
     */
    private String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    /**
     * Post data to server
     *
     * @param data             Data package for uplodaing
     *
     * @author Xiao(Sean Bo)
     *
     */
    public String postData(byte[] data) {
        Log.d(TAG,"Network:" + Boolean.toString(checkNetwork()));
        if(!checkNetwork())
            return  "Fail";
        String callBack = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("content-type",
                    "application/json; charset=utf-8");

            // Send data
            OutputStream out = new BufferedOutputStream(
                    connection.getOutputStream());
            out.write(data);
            out.flush();

            // Get call back
            InputStream in = new BufferedInputStream(
                    connection.getInputStream());
            if (in != null) {
                callBack = this.convertStreamToString(in);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Crashlytics.logException(e);
            return "Fail";
        } finally {
            connection.disconnect();
            return callBack;
        }
    }

    private boolean checkNetwork(){
        ConnectivityManager connMgr = (ConnectivityManager) MyApplication.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if(networkInfo != null && networkInfo.isConnected()){
            return true;
        }
        return false;
    }
}

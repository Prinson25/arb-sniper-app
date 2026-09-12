package com.arbbot.sniper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private SharedPreferences prefs;
    private String deviceId;
    private final String WORKER_URL = "https://lively-bird-e817.prinsonlobo25.workers.dev";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("ArbAppPrefs", Context.MODE_PRIVATE);
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        // Create WebView dynamically (no XML layout needed)
        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(webView);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                checkLicenseAndInject();
            }
        });

        webView.loadUrl("https://paywivo.com");
    }

    private void checkLicenseAndInject() {
        String savedKey = prefs.getString("license_key", null);
        if (savedKey == null || savedKey.trim().isEmpty()) {
            showActivationDialog();
        } else {
            validateWithServer(savedKey.trim());
        }
    }

    private void showActivationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Activate License");
        builder.setMessage("Enter your activation key:");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("e.g. ARB-VIP-001");
        builder.setView(input);

        builder.setPositiveButton("Activate", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String enteredKey = input.getText().toString().trim();
                if (!enteredKey.isEmpty()) {
                    validateWithServer(enteredKey);
                } else {
                    Toast.makeText(MainActivity.this, "Key cannot be empty", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        });

        builder.setNegativeButton("Exit", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                finish();
            }
        });

        builder.setCancelable(false);
        builder.show();
    }

    private void validateWithServer(final String key) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String queryUrl = WORKER_URL + "?key=" + key + "&device_id=" + deviceId;
                    URL url = new URL(queryUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);

                    int responseCode = conn.getResponseCode();
                    BufferedReader in;
                    if (responseCode >= 200 && responseCode < 300) {
                        in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    } else {
                        in = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                    }

                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();

                    JSONObject json = new JSONObject(response.toString());
                    final boolean success = json.optBoolean("success", false);
                    final String message = json.optString("message", "Validation failed");
                    final String script = json.optString("script", "");

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (success) {
                                prefs.edit().putString("license_key", key).apply();
                                Toast.makeText(MainActivity.this, "License Activated!", Toast.LENGTH_SHORT).show();
                                if (!script.isEmpty()) {
                                    webView.evaluateJavascript(script, null);
                                }
                            } else {
                                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                                prefs.edit().remove("license_key").apply();
                                showActivationDialog();
                            }
                        }
                    });

                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Connection error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }
}

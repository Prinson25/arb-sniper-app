package com.arbbot.sniper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
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

public class MainActivity extends Activity {

    private WebView webView;
    private SharedPreferences prefs;
    private String deviceId;
    private final String WORKER_URL = "https://lively-bird-e817.prinsonlobo25.workers.dev";
    private final String TARGET_URL = "https://wkfan.paykexo.com/#/login";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String cachedScript = "";
    private boolean isScriptInjected = false;
    private boolean isCheckingLicense = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("ArbAppPrefs", Context.MODE_PRIVATE);
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(webView);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // When page loads or refreshes, trigger activation/injection
                checkLicenseAndInject();
            }
        });

        webView.loadUrl(TARGET_URL);

        // Safe SPA mounting check:
        // Only mounts once per page load and does NOT wipe input field states
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!cachedScript.isEmpty() && webView != null && !isScriptInjected) {
                    webView.evaluateJavascript(
                        "(function() { return Boolean(document.getElementById('arb-autobuy-panel')); })();",
                        value -> {
                            if ("false".equals(value)) {
                                webView.evaluateJavascript(cachedScript, null);
                                isScriptInjected = true;
                            }
                        }
                    );
                }
                mainHandler.postDelayed(this, 3000);
            }
        }, 3000);
    }

    private void checkLicenseAndInject() {
        if (isCheckingLicense) return;

        String savedKey = prefs.getString("license_key", null);
        if (savedKey == null || savedKey.trim().isEmpty()) {
            showActivationDialog();
        } else {
            validateWithServer(savedKey.trim());
        }
    }

    private void showActivationDialog() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("⚡ Flash Activation");
                builder.setMessage("Enter your VIP License Key to activate on this device:");

                final EditText input = new EditText(MainActivity.this);
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
                            showActivationDialog();
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
        });
    }

    private void validateWithServer(final String key) {
        isCheckingLicense = true;
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
                            isCheckingLicense = false;
                            if (success && !script.isEmpty()) {
                                prefs.edit().putString("license_key", key).apply();
                                cachedScript = script;
                                isScriptInjected = true;
                                webView.evaluateJavascript(script, null);
                                Toast.makeText(MainActivity.this, "Activated successfully!", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                                prefs.edit().remove("license_key").apply();
                                isScriptInjected = false;
                                showActivationDialog();
                            }
                        }
                    });

                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            isCheckingLicense = false;
                            Toast.makeText(MainActivity.this, "Connection error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}

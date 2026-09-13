package com.arbbot.sniper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
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
    private Dialog activeDialog = null;

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
                checkLicenseAndInject();
            }
        });

        webView.loadUrl(TARGET_URL);

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
            showModernActivationDialog();
        } else {
            validateWithServer(savedKey.trim());
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private void showModernActivationDialog() {
        mainHandler.post(() -> {
            if (activeDialog != null && activeDialog.isShowing()) return;

            final Dialog dialog = new Dialog(MainActivity.this);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setCancelable(false);

            LinearLayout root = new LinearLayout(MainActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20));

            // Modern dark container background
            GradientDrawable dialogBg = new GradientDrawable();
            dialogBg.setColor(Color.parseColor("#141f32"));
            dialogBg.setCornerRadius(dpToPx(16));
            dialogBg.setStroke(dpToPx(1), Color.parseColor("#23344d"));
            root.setBackground(dialogBg);

            // Header Title
            TextView title = new TextView(MainActivity.this);
            title.setText("⚡ FLASH ACTIVATION");
            title.setTextColor(Color.parseColor("#38bdf8"));
            title.setTextSize(16);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER_HORIZONTAL);
            root.addView(title);

            // Subtitle
            TextView subtitle = new TextView(MainActivity.this);
            subtitle.setText("Enter your VIP key to link this device:");
            subtitle.setTextColor(Color.parseColor("#94a3b8"));
            subtitle.setTextSize(13);
            subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            subParams.setMargins(0, dpToPx(6), 0, dpToPx(16));
            subtitle.setLayoutParams(subParams);
            root.addView(subtitle);

            // Modern input box
            final EditText input = new EditText(MainActivity.this);
            input.setHint("e.g. ARB-VIP-001");
            input.setHintTextColor(Color.parseColor("#64748b"));
            input.setTextColor(Color.WHITE);
            input.setTextSize(14);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));

            GradientDrawable inputBg = new GradientDrawable();
            inputBg.setColor(Color.parseColor("#0d1829"));
            inputBg.setCornerRadius(dpToPx(10));
            inputBg.setStroke(dpToPx(1), Color.parseColor("#23344d"));
            input.setBackground(inputBg);

            LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            inputParams.setMargins(0, 0, 0, dpToPx(18));
            input.setLayoutParams(inputParams);
            root.addView(input);

            // Button container
            LinearLayout btnRow = new LinearLayout(MainActivity.this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setWeightSum(2);

            // Exit Button
            Button btnExit = new Button(MainActivity.this);
            btnExit.setText("EXIT");
            btnExit.setTextColor(Color.parseColor("#cbd5e1"));
            btnExit.setTextSize(13);
            GradientDrawable exitBg = new GradientDrawable();
            exitBg.setColor(Color.parseColor("#223247"));
            exitBg.setCornerRadius(dpToPx(10));
            btnExit.setBackground(exitBg);

            LinearLayout.LayoutParams exitParams = new LinearLayout.LayoutParams(
                    0, dpToPx(44), 1);
            exitParams.setMargins(0, 0, dpToPx(6), 0);
            btnExit.setLayoutParams(exitParams);
            btnExit.setOnClickListener(v -> {
                dialog.dismiss();
                finish();
            });
            btnRow.addView(btnExit);

            // Activate Button
            Button btnActivate = new Button(MainActivity.this);
            btnActivate.setText("ACTIVATE");
            btnActivate.setTextColor(Color.WHITE);
            btnActivate.setTextSize(13);
            GradientDrawable actBg = new GradientDrawable();
            actBg.setColor(Color.parseColor("#2563eb"));
            actBg.setCornerRadius(dpToPx(10));
            btnActivate.setBackground(actBg);

            LinearLayout.LayoutParams actParams = new LinearLayout.LayoutParams(
                    0, dpToPx(44), 1);
            actParams.setMargins(dpToPx(6), 0, 0, 0);
            btnActivate.setLayoutParams(actParams);
            btnActivate.setOnClickListener(v -> {
                String enteredKey = input.getText().toString().trim();
                if (!enteredKey.isEmpty()) {
                    btnActivate.setEnabled(false);
                    btnActivate.setText("VALIDATING...");
                    validateWithServer(enteredKey);
                } else {
                    Toast.makeText(MainActivity.this, "Key cannot be empty", Toast.LENGTH_SHORT).show();
                }
            });
            btnRow.addView(btnActivate);

            root.addView(btnRow);

            dialog.setContentView(root);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                int dialogWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.88);
                dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            }

            activeDialog = dialog;
            dialog.show();
        });
    }

    private void dismissActiveDialog() {
        if (activeDialog != null && activeDialog.isShowing()) {
            activeDialog.dismiss();
            activeDialog = null;
        }
    }

    private void validateWithServer(final String key) {
        isCheckingLicense = true;
        executor.execute(() -> {
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

                mainHandler.post(() -> {
                    isCheckingLicense = false;
                    if (success && !script.isEmpty()) {
                        prefs.edit().putString("license_key", key).apply();
                        cachedScript = script;
                        isScriptInjected = true;
                        dismissActiveDialog();
                        webView.evaluateJavascript(script, null);
                        Toast.makeText(MainActivity.this, "⚡ Activated successfully!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                        prefs.edit().remove("license_key").apply();
                        isScriptInjected = false;
                        dismissActiveDialog();
                        showModernActivationDialog();
                    }
                });

            } catch (final Exception e) {
                mainHandler.post(() -> {
                    isCheckingLicense = false;
                    Toast.makeText(MainActivity.this, "Connection error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    dismissActiveDialog();
                    showModernActivationDialog();
                });
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

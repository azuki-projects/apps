package com.azuki.groqchat;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.app.AlertDialog;
import android.text.InputType;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class ChatOverlay {
    private final Context context;
    private final WindowManager wm;
    private final SharedPreferences prefs;
    private WindowManager.LayoutParams params;
    private View current;
    private LinearLayout root, messages;
    private ScrollView scroll;
    private EditText composer;
    private TextView status;
    private final ArrayList<JSONObject> history = new ArrayList<>();
    private boolean compact;
    private boolean busy;
    private int downX, downY, startX, startY;
    private boolean dragging;

    private final int BG = Color.rgb(17, 22, 29);
    private final int PANEL = Color.rgb(29, 37, 47);
    private final int GREEN = Color.rgb(31, 190, 132);
    private final int WHITE = Color.WHITE;
    private final int MUTED = Color.rgb(158, 174, 185);

    public ChatOverlay(Context context) {
        this.context = context;
        wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        prefs = context.getSharedPreferences("groq_float", Context.MODE_PRIVATE);
        compact = prefs.getBoolean("compact", false);
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = prefs.getInt("x", 24);
        params.y = prefs.getInt("y", 120);
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
    }

    private int dp(float n) { return (int)(n * context.getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), Color.argb(35, 255, 255, 255));
        return d;
    }

    private TextView label(String value, int size, int color) {
        TextView t = new TextView(context);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    public void show() { render(); }

    private void render() {
        if (current != null) try { wm.removeView(current); } catch (Exception ignored) { }
        current = compact ? bubble() : panel();
        try { wm.addView(current, params); } catch (Exception ignored) { }
        prefs.edit().putBoolean("compact", compact).apply();
    }

    private View bubble() {
        TextView b = label("AI", 19, WHITE);
        b.setBackground(shape(GREEN, 40)); b.setElevation(dp(10));
        b.setWidth(dp(58)); b.setHeight(dp(58));
        b.setOnTouchListener((v,e) -> drag(v,e,true));
        return b;
    }

    private View panel() {
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(11), dp(9), dp(11), dp(10));
        root.setBackground(shape(BG, 20)); root.setElevation(dp(14));
        root.setLayoutParams(new LinearLayout.LayoutParams(dp(320), dp(470)));

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("GROQ  /  GPT-OSS-120B", 12, MUTED);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        title.setLetterSpacing(0.06f);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(38), 1));
        TextView key = label("KEY", 11, WHITE);
        key.setBackground(shape(PANEL, 10));
        header.addView(key, new LinearLayout.LayoutParams(dp(43), dp(32)));
        key.setOnClickListener(v -> showKeyDialog());
        TextView min = label("—", 22, WHITE); min.setBackground(shape(PANEL, 10));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(36), dp(32)); mp.leftMargin = dp(5);
        header.addView(min, mp);
        min.setOnClickListener(v -> { compact = true; save(); render(); });
        TextView close = label("×", 22, WHITE); close.setBackground(shape(Color.rgb(145,55,61), 10));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(36), dp(32)); cp.leftMargin = dp(5);
        header.addView(close, cp);
        close.setOnClickListener(v -> context.stopService(new Intent(context, ChatService.class)));
        header.setOnTouchListener((v,e) -> drag(v,e,false));
        root.addView(header);

        status = label("Ready · Groq API", 11, MUTED);
        status.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(24)));

        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        messages = new LinearLayout(context);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(dp(2), dp(5), dp(2), dp(8));
        scroll.addView(messages, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, 0, 1);
        sp.topMargin = dp(3); sp.bottomMargin = dp(7);
        root.addView(scroll, sp);
        addMessage("assistant", "Hi! I'm ready. Add your Groq API key using KEY, then ask me anything.");
        
        LinearLayout bottom = new LinearLayout(context);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        composer = new EditText(context);
        composer.setSingleLine(false); composer.setMinLines(1); composer.setMaxLines(3);
        composer.setTextSize(14); composer.setTextColor(WHITE); composer.setHintTextColor(MUTED);
        composer.setHint("Message Groq...");
        composer.setPadding(dp(12), dp(9), dp(12), dp(9));
        composer.setBackground(shape(PANEL, 14));
        composer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        bottom.addView(composer, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView send = label("↑", 22, WHITE); send.setBackground(shape(GREEN, 14));
        LinearLayout.LayoutParams sendp = new LinearLayout.LayoutParams(dp(48), dp(48)); sendp.leftMargin = dp(7);
        bottom.addView(send, sendp);
        send.setOnClickListener(v -> sendMessage());
        root.addView(bottom);
        if (history.size() > 0) {
            messages.removeAllViews();
            for (JSONObject item : history) addMessage(item.optString("role"), item.optString("content"));
        }
        return root;
    }

    private void addMessage(String role, String content) {
        if (messages == null) return;
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(shape(role.equals("user") ? Color.rgb(28,92,72) : PANEL, 13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(7);
        TextView who = label(role.equals("user") ? "YOU" : "GROQ", 10, role.equals("user") ? Color.rgb(178,255,218) : GREEN);
        who.setGravity(Gravity.LEFT);
        TextView body = new TextView(context);
        body.setText(content); body.setTextColor(WHITE); body.setTextSize(14);
        body.setTextIsSelectable(true); body.setPadding(0, dp(4), 0, 0);
        row.addView(who); row.addView(body);
        messages.addView(row, lp);
        if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void sendMessage() {
        if (busy) return;
        String text = composer.getText().toString().trim();
        if (text.isEmpty()) return;
        String apiKey = prefs.getString("api_key", "");
        if (apiKey.isEmpty()) { showKeyDialog(); return; }
        composer.setText("");
        addHistory("user", text);
        addMessage("user", text);
        busy = true;
        status.setText("Thinking...");
        final TextView waiting = label("Thinking…", 13, MUTED);
        waiting.setGravity(Gravity.LEFT);
        messages.addView(waiting);
        new Thread(() -> {
            String answer = requestGroq(apiKey);
            android.os.Handler main = new android.os.Handler(context.getMainLooper());
            main.post(() -> {
                busy = false;
                messages.removeView(waiting);
                addHistory("assistant", answer);
                addMessage("assistant", answer);
                status.setText(answer.startsWith("Request failed:") ? "Request error" : "Ready · Groq API");
            });
        }).start();
    }

    private void addHistory(String role, String content) {
        try { JSONObject o = new JSONObject(); o.put("role", role); o.put("content", content); history.add(o); }
        catch (Exception ignored) { }
    }

    private String requestGroq(String key) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://api.groq.com/openai/v1/chat/completions");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000); connection.setReadTimeout(60000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + key);
            connection.setRequestProperty("Content-Type", "application/json");
            JSONObject payload = new JSONObject();
            payload.put("model", "openai/gpt-oss-120b");
            JSONArray msgs = new JSONArray();
            JSONObject system = new JSONObject();
            system.put("role", "system");
            system.put("content", "You are a helpful AI assistant. Answer clearly and accurately.");
            msgs.put(system);
            for (JSONObject item : history) msgs.put(item);
            payload.put("messages", msgs);
            payload.put("temperature", 0.7);
            payload.put("max_completion_tokens", 2048);
            byte[] data = payload.toString().getBytes("UTF-8");
            connection.getOutputStream().write(data);
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            StringBuilder response = new StringBuilder();
            String line; while ((line = reader.readLine()) != null) response.append(line);
            reader.close();
            JSONObject json = new JSONObject(response.toString());
            if (code < 200 || code >= 300) {
                String message = json.optJSONObject("error") != null ? json.optJSONObject("error").optString("message") : response.toString();
                return "Request failed: " + message;
            }
            JSONArray choices = json.optJSONArray("choices");
            if (choices == null || choices.length() == 0) return "Request failed: Empty response from Groq.";
            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
            String content = message.optString("content", "");
            if (content.isEmpty()) content = "The model returned an empty message.";
            return content;
        } catch (Exception e) {
            return "Request failed: " + (e.getMessage() == null ? "Unknown network error" : e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void showKeyDialog() {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint("gsk_...");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setText(prefs.getString("api_key", ""));
        AlertDialog dialog = new AlertDialog.Builder(context, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Groq API key")
                .setMessage("Your key is stored locally on this device.")
                .setView(input)
                .setPositiveButton("Save", (d,w) -> {
                    String value = input.getText().toString().trim();
                    prefs.edit().putString("api_key", value).apply();
                    if (status != null) status.setText(value.isEmpty() ? "API key not set" : "API key saved · Ready");
                })
                .setNeutralButton("Clear", (d,w) -> {
                    prefs.edit().remove("api_key").apply();
                    if (status != null) status.setText("API key cleared");
                })
                .setNegativeButton("Cancel", null).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.show();
    }

    private boolean drag(View view, MotionEvent event, boolean isBubble) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = (int)event.getRawX(); downY = (int)event.getRawY();
                startX = params.x; startY = params.y; dragging = false; return true;
            case MotionEvent.ACTION_MOVE:
                int dx = (int)event.getRawX() - downX, dy = (int)event.getRawY() - downY;
                if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) dragging = true;
                if (dragging) {
                    params.x = startX + dx; params.y = startY + dy;
                    try { wm.updateViewLayout(current, params); } catch (Exception ignored) { }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging && isBubble) { compact = false; render(); }
                save(); return true;
        }
        return false;
    }

    private void save() {
        prefs.edit().putInt("x", params.x).putInt("y", params.y).putBoolean("compact", compact).apply();
    }

    public void dismiss() {
        if (current != null) { try { wm.removeView(current); } catch (Exception ignored) { } current = null; }
    }
}
package com.azuki.calculatorfloat;

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
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class CalculatorOverlay {
    private final Context context;
    private final WindowManager windowManager;
    private final SharedPreferences prefs;
    private WindowManager.LayoutParams params;
    private View currentView;
    private LinearLayout panel;
    private TextView expressionView;
    private TextView resultView;
    private String expression = "";
    private boolean compact;
    private boolean justEvaluated;
    private int downX, downY, startX, startY;
    private boolean dragging;

    public CalculatorOverlay(Context context) {
        this.context = context;
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        prefs = context.getSharedPreferences("calc_float", Context.MODE_PRIVATE);
        compact = prefs.getBoolean("compact", false);
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = prefs.getInt("x", 28);
        params.y = prefs.getInt("y", 150);
    }

    public void show() { showCurrent(); }

    private int dp(float value) { return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), Color.argb(35, 255, 255, 255));
        return d;
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(context);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void showCurrent() {
        if (currentView != null) {
            try { windowManager.removeView(currentView); } catch (Exception ignored) { }
        }
        currentView = compact ? makeBubble() : makePanel();
        try { windowManager.addView(currentView, params); } catch (Exception ignored) { }
        prefs.edit().putBoolean("compact", compact).apply();
    }

    private View makeBubble() {
        TextView bubble = text("÷", 29, Color.WHITE);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackground(shape(Color.rgb(27, 190, 130), 50));
        bubble.setElevation(dp(9));
        bubble.setWidth(dp(58));
        bubble.setHeight(dp(58));
        bubble.setOnTouchListener((v, event) -> handleDrag(v, event, true));
        return bubble;
    }

    private View makePanel() {
        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(12));
        panel.setBackground(shape(Color.rgb(19, 24, 29), 22));
        panel.setElevation(dp(12));
        panel.setLayoutParams(new LinearLayout.LayoutParams(dp(300), -2));

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("CALC FLOAT", 12, Color.rgb(150, 164, 174));
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        title.setLetterSpacing(0.12f);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(34), 1));
        TextView minimize = text("—", 22, Color.WHITE);
        minimize.setBackground(shape(Color.rgb(43, 52, 60), 12));
        header.addView(minimize, new LinearLayout.LayoutParams(dp(38), dp(32)));
        minimize.setOnClickListener(v -> { compact = true; savePosition(); showCurrent(); });
        TextView close = text("×", 21, Color.WHITE);
        close.setBackground(shape(Color.rgb(145, 55, 61), 12));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(38), dp(32));
        closeParams.leftMargin = dp(6);
        header.addView(close, closeParams);
        close.setOnClickListener(v -> context.stopService(new android.content.Intent(context, CalculatorService.class)));
        header.setOnTouchListener((v, event) -> handleDrag(v, event, false));
        panel.addView(header);

        expressionView = text("", 20, Color.rgb(176, 190, 199));
        expressionView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        expressionView.setSingleLine(true);
        expressionView.setPadding(dp(4), dp(8), dp(4), 0);
        panel.addView(expressionView, new LinearLayout.LayoutParams(-1, dp(42)));

        resultView = text("0", 34, Color.WHITE);
        resultView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        resultView.setSingleLine(true);
        resultView.setPadding(dp(4), 0, dp(4), dp(12));
        panel.addView(resultView, new LinearLayout.LayoutParams(-1, dp(58)));

        String[][] keys = {
                {"C", "DEL", "(", ")"},
                {"%", "^", "√", "÷"},
                {"7", "8", "9", "×"},
                {"4", "5", "6", "−"},
                {"1", "2", "3", "+"},
                {"±", "0", ".", "="}
        };
        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(4);
        grid.setRowCount(keys.length);
        for (int r = 0; r < keys.length; r++) {
            for (int c = 0; c < keys[r].length; c++) {
                String key = keys[r][c];
                TextView button = text(key, key.length() > 2 ? 14 : 19, Color.WHITE);
                int color = isOperator(key) || key.equals("=") ? Color.rgb(27, 190, 130)
                        : (key.equals("C") || key.equals("DEL") ? Color.rgb(61, 74, 84) : Color.rgb(35, 43, 51));
                button.setBackground(shape(color, 13));
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                        GridLayout.spec(r), GridLayout.spec(c, 1f));
                lp.width = 0;
                lp.height = dp(46);
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
                grid.addView(button, lp);
                button.setOnClickListener(v -> press(key));
            }
        }
        panel.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        updateDisplay();
        return panel;
    }

    private boolean isOperator(String key) {
        return key.equals("÷") || key.equals("×") || key.equals("−") || key.equals("+") || key.equals("^");
    }

    private void press(String key) {
        if (key.equals("C")) {
            expression = ""; justEvaluated = false;
        } else if (key.equals("DEL")) {
            if (!expression.isEmpty()) expression = expression.substring(0, expression.length() - 1);
            justEvaluated = false;
        } else if (key.equals("=")) {
            try {
                expression = ExpressionEngine.calculate(expression);
                justEvaluated = true;
            } catch (Exception e) {
                resultView.setText("Error");
                return;
            }
        } else if (key.equals("±")) {
            if (expression.isEmpty()) {
                expression = "−";
            } else {
                int start = expression.length() - 1;
                while (start >= 0 && (Character.isDigit(expression.charAt(start)) || expression.charAt(start) == '.')) start--;
                String number = expression.substring(start + 1);
                if (!number.isEmpty()) {
                    if (start >= 0 && expression.charAt(start) == '−' && (start == 0 || "(+−×÷^".indexOf(expression.charAt(start - 1)) >= 0)) {
                        expression = expression.substring(0, start) + number;
                    } else {
                        expression = expression.substring(0, start + 1) + "−" + number;
                    }
                }
            }
            justEvaluated = false;
        } else if (key.equals("√")) {
            if (justEvaluated) { expression = ""; justEvaluated = false; }
            expression += "sqrt(";
        } else {
            if (justEvaluated && (Character.isDigit(key.charAt(0)) || key.equals("."))) expression = "";
            justEvaluated = false;
            if (isOperator(key)) {
                if (expression.isEmpty()) {
                    if (key.equals("−")) expression = key;
                } else {
                    char last = expression.charAt(expression.length() - 1);
                    if ("+−×÷^".indexOf(last) >= 0) expression = expression.substring(0, expression.length() - 1) + key;
                    else expression += key;
                }
            } else if (key.equals(".")) {
                int i = expression.length() - 1;
                while (i >= 0 && (Character.isDigit(expression.charAt(i)) || expression.charAt(i) == '.')) i--;
                String current = expression.substring(i + 1);
                if (!current.contains(".")) expression += current.isEmpty() ? "0." : ".";
            } else if (key.equals("%")) {
                if (!expression.isEmpty() && Character.isDigit(expression.charAt(expression.length() - 1))) expression += "%";
            } else {
                expression += key;
            }
        }
        updateDisplay();
    }

    private void updateDisplay() {
        if (expressionView == null || resultView == null) return;
        expressionView.setText(expression.isEmpty() ? " " : expression);
        if (expression.isEmpty()) { resultView.setText("0"); return; }
        try { resultView.setText(ExpressionEngine.calculate(expression)); }
        catch (Exception ignored) { resultView.setText(" "); }
    }

    private boolean handleDrag(View view, MotionEvent event, boolean bubble) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = (int) event.getRawX(); downY = (int) event.getRawY();
                startX = params.x; startY = params.y; dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                int dx = (int) event.getRawX() - downX;
                int dy = (int) event.getRawY() - downY;
                if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) dragging = true;
                if (dragging) {
                    params.x = startX + dx; params.y = startY + dy;
                    try { windowManager.updateViewLayout(currentView, params); } catch (Exception ignored) { }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging && bubble) { compact = false; showCurrent(); }
                savePosition();
                return true;
        }
        return false;
    }

    private void savePosition() {
        prefs.edit().putInt("x", params.x).putInt("y", params.y).putBoolean("compact", compact).apply();
    }

    public void dismiss() {
        if (currentView != null) {
            try { windowManager.removeView(currentView); } catch (Exception ignored) { }
            currentView = null;
        }
    }
}

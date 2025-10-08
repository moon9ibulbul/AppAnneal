package com.example.permutasi;

import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class CrashActivity extends AppCompatActivity {
  @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    String trace = getIntent().getStringExtra("trace");
    if (trace == null) trace = "(no stack trace)";

    TextView tv = new TextView(this);
    tv.setTextIsSelectable(true);
    tv.setText(trace);
    tv.setPadding(24,24,24,24);
    tv.setTextSize(12f);
    ScrollView sv = new ScrollView(this);
    sv.addView(tv);
    setContentView(sv);
  }
}

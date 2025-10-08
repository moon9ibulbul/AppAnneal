package com.example.permutasi;

import android.app.Application;
import android.content.Intent;

public class App extends Application {
  @Override public void onCreate() {
    super.onCreate();
    Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
      Intent i = new Intent(this, CrashActivity.class);
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
      i.putExtra("trace", getStack(e));
      startActivity(i);
      // biar process mati setelah tampil crash screen
      android.os.Process.killProcess(android.os.Process.myPid());
      System.exit(10);
    });
  }

  private static String getStack(Throwable e) {
    StringBuilder sb = new StringBuilder();
    while (e != null) {
      sb.append(e.toString()).append("\n");
      for (StackTraceElement el : e.getStackTrace()) {
        sb.append("  at ").append(el.toString()).append("\n");
      }
      e = e.getCause();
      if (e != null) sb.append("Caused by: ");
    }
    return sb.toString();
  }
}

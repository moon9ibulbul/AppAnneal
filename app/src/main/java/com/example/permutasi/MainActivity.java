package com.example.permutasi;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.permutasi.core.BitmapIO;
import com.example.permutasi.core.PermutationAnnealer2D;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

  private Button btnPickImage, btnRun, btnCopy;
  private EditText etRows, etCols, etIterations, etTemp;
  private ProgressBar progressBar;
  private TextView tvLog;

  private Uri pickedImage = null;
  private String lastPermutationText = "";

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  private final ActivityResultLauncher<String> imagePicker =
      registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
        if (uri != null) {
          pickedImage = uri;
          toast("Gambar dipilih: " + uri);
        }
      });

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    btnPickImage = findViewById(R.id.btnPickImage);
    btnRun = findViewById(R.id.btnRun);
    btnCopy = findViewById(R.id.btnCopy);
    etRows = findViewById(R.id.etRows);
    etCols = findViewById(R.id.etCols);
    etIterations = findViewById(R.id.etIterations);
    etTemp = findViewById(R.id.etTemp);
    progressBar = findViewById(R.id.progressBar);
    tvLog = findViewById(R.id.tvLog);

    btnPickImage.setOnClickListener(v -> imagePicker.launch("image/*"));
    btnRun.setOnClickListener(v -> runAnneal());
    btnCopy.setOnClickListener(v -> copyPermutation());
  }

  private void runAnneal() {
    if (pickedImage == null) { toast("Pilih gambar dulu"); return; }
    String rStr = etRows.getText().toString().trim();
    String cStr = etCols.getText().toString().trim();
    String itStr = etIterations.getText().toString().trim();
    String tStr = etTemp.getText().toString().trim();
    if (TextUtils.isEmpty(rStr) || TextUtils.isEmpty(cStr) || TextUtils.isEmpty(itStr) || TextUtils.isEmpty(tStr)) {
      toast("Isi rows, cols, iterations, temperature");
      return;
    }
    int rows, cols; long iterations; double temp;
    try { rows = Integer.parseInt(rStr); } catch (Exception e) { toast("Rows invalid"); return; }
    try { cols = Integer.parseInt(cStr); } catch (Exception e) { toast("Cols invalid"); return; }
    try { iterations = Long.parseLong(itStr); } catch (Exception e) { toast("Iterations invalid"); return; }
    try { temp = Double.parseDouble(tStr); } catch (Exception e) { toast("Temperature invalid"); return; }
    if (rows <= 0 || cols <= 0) { toast("Rows/Cols harus > 0"); return; }

    btnRun.setEnabled(false);
    progressBar.setProgress(0);
    tvLog.setText("Memulai…\n");

    executor.execute(() -> {
      try {
        Bitmap bmp = BitmapIO.read(getContentResolver(), pickedImage);
        int W = bmp.getWidth(), H = bmp.getHeight();
        if (W % cols != 0 || H % rows != 0) {
          runOnUiThread(() ->
              tvLog.append(String.format(Locale.US,
                  "⚠️ Ukuran gambar (%dx%d) tidak habis dibagi rows=%d, cols=%d\n", W, H, rows, cols)));
        }

        PermutationAnnealer2D.ProgressListener listener = (sec, donePct, iter, temperature, energy) ->
            runOnUiThread(() -> {
              progressBar.setProgress((int)Math.min(100, Math.round(donePct)));
              String line = String.format(Locale.US,
                  "t=%.2f E=%d iter=%d sec=%d (%.1f%%)\n", temperature, energy, iter, sec, donePct);
              tvLog.setText(line);
            });

        int[] perm = PermutationAnnealer2D.solve(bmp, rows, cols, iterations, temp, listener);

        // Format permutasi jadi: [a,b,c;d,e,f;...]
        lastPermutationText = formatPermutationMatrix(perm, rows, cols);
        runOnUiThread(() -> {
          tvLog.append("\nPERMUTASI:\n" + lastPermutationText + "\n");
          btnRun.setEnabled(true);
          toast("Selesai");
        });
      } catch (IOException e) {
        runOnUiThread(() -> {
          tvLog.append("\nGagal: " + e.getMessage() + "\n");
          btnRun.setEnabled(true);
        });
      }
    });
  }

  private void copyPermutation() {
    if (TextUtils.isEmpty(lastPermutationText)) { toast("Belum ada permutasi"); return; }
    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    cm.setPrimaryClip(ClipData.newPlainText("perm", lastPermutationText));
    toast("Permutasi disalin");
  }

  private static String formatPermutationMatrix(int[] perm, int rows, int cols) {
    // perm panjang N, indeks posisi 0..N-1 (row-major). Nilai = ID tile asal 0..N-1
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        int idx = r * cols + c;
        sb.append(perm[idx]);
        if (c + 1 < cols) sb.append(',');
      }
      if (r + 1 < rows) sb.append(';');
    }
    sb.append(']');
    return sb.toString();
  }

  private void toast(String s) { runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_SHORT).show()); }
}

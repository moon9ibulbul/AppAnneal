package com.example.permutasi;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.permutasi.core.BitmapIO;
import com.example.permutasi.core.PermutationAnnealer2D;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

  private Button btnPickImage, btnPickOriginal, btnRun, btnCopy, btnApplyPermutation;
  private EditText etRows, etCols, etIterations, etTemp, etPermutation;
  private ProgressBar progressBar;
  private TextView tvLog;
  private ImageView ivOriginal, ivInput, ivOutput;

  private Uri pickedImage = null;
  private Uri pickedOriginal = null;
  private String lastPermutationText = "";
  private int[] lastPerm0;        // 0-based internal
  private int lastRows = 0, lastCols = 0;
  private Bitmap srcBitmap = null;
  private Bitmap refBitmap = null;

  // Toggles overlay angka (default: ON supaya langsung kelihatan)
  private boolean showNumbersOriginal = true;
  private boolean showNumbersInput = true;
  private boolean showNumbersOutput = true;

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  private final ActivityResultLauncher<String> imagePicker =
      registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
        if (uri != null) {
          pickedImage = uri;
          toast("Gambar dipilih: " + uri);
          loadInputPreviewAsync();
        }
      });

  private final ActivityResultLauncher<String> originalPicker =
      registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
        if (uri != null) {
          pickedOriginal = uri;
          toast("Gambar referensi dipilih: " + uri);
          loadOriginalPreviewAsync();
        }
      });

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    btnPickImage = findViewById(R.id.btnPickImage);
    btnPickOriginal = findViewById(R.id.btnPickOriginal);
    btnRun = findViewById(R.id.btnRun);
    btnCopy = findViewById(R.id.btnCopy);
    btnApplyPermutation = findViewById(R.id.btnApplyPermutation);

    etRows = findViewById(R.id.etRows);
    etCols = findViewById(R.id.etCols);
    etIterations = findViewById(R.id.etIterations);
    etTemp = findViewById(R.id.etTemp);
    etPermutation = findViewById(R.id.etPermutation);

    progressBar = findViewById(R.id.progressBar);
    tvLog = findViewById(R.id.tvLog);
    ivOriginal = findViewById(R.id.ivOriginal);
    ivInput = findViewById(R.id.ivInput);
    ivOutput = findViewById(R.id.ivOutput);

    btnPickImage.setOnClickListener(v -> imagePicker.launch("image/*"));
    btnPickOriginal.setOnClickListener(v -> originalPicker.launch("image/*"));
    btnRun.setOnClickListener(v -> runAnneal());
    btnCopy.setOnClickListener(v -> copyPermutation());
    btnApplyPermutation.setOnClickListener(v -> applyManualPermutation());

    // Long-press toggle angka untuk masing-masing preview
    ivOriginal.setOnLongClickListener(v -> { showNumbersOriginal = !showNumbersOriginal; refreshOriginalPreview(); return true; });
    ivInput.setOnLongClickListener(v -> { showNumbersInput = !showNumbersInput; refreshInputPreview(); return true; });
    ivOutput.setOnLongClickListener(v -> { showNumbersOutput = !showNumbersOutput; refreshOutputPreview(); return true; });
  }

  // ==== PREVIEW ORIGINAL ====

  private void loadOriginalPreviewAsync() {
    if (pickedOriginal == null) return;
    executor.execute(() -> {
      try {
        refBitmap = BitmapIO.read(getContentResolver(), pickedOriginal);
        runOnUiThread(this::refreshOriginalPreview);
      } catch (IOException e) {
        runOnUiThread(() -> toast("Gagal baca referensi: " + e.getMessage()));
      }
    });
  }

  private void refreshOriginalPreview() {
    if (refBitmap == null) return;
    Integer r = tryParseInt(etRows.getText().toString().trim());
    Integer c = tryParseInt(etCols.getText().toString().trim());

    if (showNumbersOriginal && r != null && c != null && r > 0 && c > 0) {
      try {
        int[] idPerm = new int[r * c];
        for (int i = 0; i < idPerm.length; i++) idPerm[i] = i;
        Bitmap numbered = PermutationAnnealer2D.reconstructWithNumbers(refBitmap, r, c, idPerm);
        setImageFullSize(ivOriginal, numbered);
        ivOriginal.setContentDescription("Original Preview (numbers ON)");
        toastQuick("Angka referensi: ON");
        return;
      } catch (Throwable ignored) {
        // fallback ke plain jika gagal
      }
    }
    setImageFullSize(ivOriginal, refBitmap);
    ivOriginal.setContentDescription("Original Preview (numbers OFF)");
    if (showNumbersOriginal) toastQuick("Angka referensi: OFF (rows/cols belum valid)");
    else toastQuick("Angka referensi: OFF");
  }

  // ==== PREVIEW INPUT ====

  private void loadInputPreviewAsync() {
    if (pickedImage == null) return;
    executor.execute(() -> {
      try {
        srcBitmap = BitmapIO.read(getContentResolver(), pickedImage);
        runOnUiThread(this::refreshInputPreview);
      } catch (IOException e) {
        runOnUiThread(() -> toast("Gagal baca gambar: " + e.getMessage()));
      }
    });
  }

  private void refreshInputPreview() {
    if (srcBitmap == null) return;
    // Coba ambil rows/cols dari input user agar overlay angka bisa digambar sebagai grid.
    Integer r = tryParseInt(etRows.getText().toString().trim());
    Integer c = tryParseInt(etCols.getText().toString().trim());

    if (showNumbersInput && r != null && c != null && r > 0 && c > 0) {
      try {
        int[] idPerm = new int[r * c];
        for (int i = 0; i < idPerm.length; i++) idPerm[i] = i; // identitas
        Bitmap numbered = PermutationAnnealer2D.reconstructWithNumbers(srcBitmap, r, c, idPerm);
        setImageFullSize(ivInput, numbered);
        ivInput.setContentDescription("Input Preview (numbers ON)");
        toastQuick("Angka input: ON");
        return;
      } catch (Throwable ignored) {
        // fallback ke plain jika gagal
      }
    }
    setImageFullSize(ivInput, srcBitmap);
    ivInput.setContentDescription("Input Preview (numbers OFF)");
    if (showNumbersInput) toastQuick("Angka input: OFF (rows/cols belum valid)");
    else toastQuick("Angka input: OFF");
  }

  // ==== PREVIEW OUTPUT ====

  private void refreshOutputPreview() {
    if (srcBitmap == null || lastPerm0 == null || lastRows <= 0 || lastCols <= 0) return;
    Bitmap out = showNumbersOutput
        ? PermutationAnnealer2D.reconstructWithNumbers(srcBitmap, lastRows, lastCols, lastPerm0)
        : PermutationAnnealer2D.reconstruct(srcBitmap, lastRows, lastCols, lastPerm0);
    setImageFullSize(ivOutput, out);
    ivOutput.setContentDescription(showNumbersOutput ? "Output Preview (numbers ON)" : "Output Preview (numbers OFF)");
    toastQuick(showNumbersOutput ? "Angka output: ON" : "Angka output: OFF");
  }

  // ==== PROSES ANNEAL ====

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
        if (srcBitmap == null) {
          srcBitmap = BitmapIO.read(getContentResolver(), pickedImage);
          runOnUiThread(this::refreshInputPreview);
        }
        if (refBitmap == null && pickedOriginal != null) {
          refBitmap = BitmapIO.read(getContentResolver(), pickedOriginal);
          runOnUiThread(this::refreshOriginalPreview);
        }
        int W = srcBitmap.getWidth(), H = srcBitmap.getHeight();
        if (W % cols != 0 || H % rows != 0) {
          runOnUiThread(() ->
              tvLog.append(String.format(Locale.US,
                  "⚠️ Ukuran gambar (%dx%d) tidak habis dibagi rows=%d, cols=%d\n", W, H, rows, cols)));
        }

        int[] perm0;
        if (refBitmap != null) {
          runOnUiThread(() -> tvLog.setText("Menggunakan gambar referensi untuk mencocokkan tile...\n"));
          PermutationAnnealer2D.TileMatchProgressListener listener = (done, total) ->
              runOnUiThread(() -> {
                int pct = total <= 0 ? 0 : (int) Math.min(100, Math.round(done * 100f / total));
                progressBar.setProgress(pct);
                tvLog.setText(String.format(Locale.US,
                    "Mencocokkan tile %d/%d\n", done, total));
              });
          perm0 = PermutationAnnealer2D.solveUsingReference(srcBitmap, refBitmap, rows, cols, listener);
        } else {
          PermutationAnnealer2D.ProgressListener listener = (sec, donePct, iter, temperature, energy) ->
              runOnUiThread(() -> {
                progressBar.setProgress((int)Math.min(100, Math.round(donePct)));
                String line = String.format(Locale.US,
                    "t=%.2f E=%d iter=%d sec=%d (%.1f%%)\n", temperature, energy, iter, sec, donePct);
                tvLog.setText(line);
              });

          perm0 = PermutationAnnealer2D.solve(srcBitmap, rows, cols, iterations, temp, listener);
        }
        lastPerm0 = perm0;
        lastRows = rows; lastCols = cols;

        lastPermutationText = formatPermutationMatrix1Based(perm0, rows, cols);
        runOnUiThread(() -> {
          // Tampilkan output sesuai toggle
          refreshOutputPreview();
          etPermutation.setText(lastPermutationText);
          tvLog.append("\nPERMUTASI (1-based):\n" + lastPermutationText + "\n");
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

  // ==== MANUAL PERMUTATION ====

  private void applyManualPermutation() {
    if (srcBitmap == null) { toast("Belum ada gambar"); return; }
    String txt = etPermutation.getText().toString().trim();
    if (TextUtils.isEmpty(txt)) { toast("Isi permutasi dulu"); return; }
    if (lastRows <= 0 || lastCols <= 0) {
      String rStr = etRows.getText().toString().trim();
      String cStr = etCols.getText().toString().trim();
      try { lastRows = Integer.parseInt(rStr); } catch (Exception ignored) {}
      try { lastCols = Integer.parseInt(cStr); } catch (Exception ignored) {}
      if (lastRows <= 0 || lastCols <= 0) { toast("Rows/Cols belum valid"); return; }
    }

    try {
      int[] perm1 = parsePermutation1Based(txt, lastRows, lastCols);
      int[] perm0 = new int[perm1.length];
      for (int i = 0; i < perm1.length; i++) {
        perm0[i] = perm1[i] - 1;
        if (perm0[i] < 0) throw new IllegalArgumentException("Index < 1 pada posisi " + i);
      }
      lastPerm0 = perm0;
      // Tampilkan output sesuai toggle
      refreshOutputPreview();
      toast("Permutasi diterapkan");
    } catch (Exception ex) {
      toast("Permutasi invalid: " + ex.getMessage());
    }
  }

  private void copyPermutation() {
    if (TextUtils.isEmpty(lastPermutationText)) { toast("Belum ada permutasi"); return; }
    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    cm.setPrimaryClip(ClipData.newPlainText("perm", lastPermutationText));
    toast("Permutasi (1-based) disalin");
  }

  // ==== UTIL ====

  /** Tampilkan bitmap pada ukuran asli (1:1) di dalam ImageView, bisa di-scroll. */
  private void setImageFullSize(ImageView view, Bitmap bmp) {
    if (bmp == null) return;
    view.setImageBitmap(bmp);
    if (view.getLayoutParams() != null) {
      view.getLayoutParams().width = bmp.getWidth();
      view.getLayoutParams().height = bmp.getHeight();
      view.requestLayout();
    }
  }

  private static String formatPermutationMatrix1Based(int[] perm0, int rows, int cols) {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        int idx = r * cols + c;
        sb.append(perm0[idx] + 1);
        if (c + 1 < cols) sb.append(',');
      }
      if (r + 1 < rows) sb.append(';');
    }
    sb.append(']');
    return sb.toString();
  }

  /** Parse teks 1-based ke array panjang rows*cols. Format: [a,b,c;d,e,f;...] */
  private static int[] parsePermutation1Based(String text, int rows, int cols) {
    String t = text.trim();
    if (t.startsWith("[")) t = t.substring(1);
    if (t.endsWith("]")) t = t.substring(0, t.length()-1);
    String[] rowParts = t.split(";");
    if (rowParts.length != rows) throw new IllegalArgumentException("Jumlah baris != rows");
    ArrayList<Integer> vals = new ArrayList<>(rows * cols);
    for (String row : rowParts) {
      row = row.trim();
      if (row.isEmpty()) throw new IllegalArgumentException("Baris kosong");
      String[] xs = row.split(",");
      if (xs.length != cols) throw new IllegalArgumentException("Jumlah kolom != cols");
      for (String s : xs) {
        int v = Integer.parseInt(s.trim());
        vals.add(v);
      }
    }
    int[] out = new int[vals.size()];
    for (int i = 0; i < out.length; i++) out[i] = vals.get(i);
    return out;
  }

  private Integer tryParseInt(String s) {
    try { return Integer.parseInt(s); } catch (Exception e) { return null; }
  }

  private void toast(String s) { runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_SHORT).show()); }

  private void toastQuick(String s) {
    // Hindari toast beruntun terlalu panjang
    if (!TextUtils.isEmpty(s)) Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
  }
}

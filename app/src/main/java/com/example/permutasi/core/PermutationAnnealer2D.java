package com.example.permutasi.core;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.Random;

public final class PermutationAnnealer2D {

  public interface ProgressListener {
    void onStatus(long seconds, double donePct, long iter, double temperature, long energy);
  }

  private PermutationAnnealer2D() {}

  /** Jalankan annealing untuk menyusun grid tiles (rows×cols). Return permutasi 0-based panjang N=rows*cols. */
  public static int[] solve(Bitmap src, int rows, int cols, long iterations, double startTemp,
                            ProgressListener cb) {
    if (rows <= 0 || cols <= 0) throw new IllegalArgumentException("rows/cols harus > 0");
    if (iterations <= 0) throw new IllegalArgumentException("iterations harus > 0");
    if (startTemp <= 0) throw new IllegalArgumentException("startTemp harus > 0");

    final int W = src.getWidth(), H = src.getHeight();
    final int tileW = Math.max(1, W / cols);
    final int tileH = Math.max(1, H / rows);
    final int N = rows * cols;

    int[] pixels = new int[W * H];
    src.getPixels(pixels, 0, W, 0, 0, W, H);

    int[][] diffH = new int[N][N];
    int[][] diffV = new int[N][N];
    for (int a = 0; a < N; a++) {
      int ar = a / cols, ac = a % cols;
      for (int b = 0; b < N; b++) {
        int br = b / cols, bc = b % cols;
        diffH[a][b] = edgeDiff(pixels, W, H, ar, ac, br, bc, tileW, tileH, true);
        diffV[a][b] = edgeDiff(pixels, W, H, ar, ac, br, bc, tileW, tileH, false);
      }
    }

    int[] pos2tile = new int[N];
    for (int i = 0; i < N; i++) pos2tile[i] = i;

    long energy = totalEnergy(pos2tile, rows, cols, diffH, diffV);

    Random rnd = new Random();
    long startTime = System.currentTimeMillis();
    long nextTick = startTime;

    for (long it = 0; it < iterations; it++) {
      double t = (double) it / (double) iterations;
      double temperature = (1.0 - t) * startTemp;

      int i = rnd.nextInt(N);
      int j = rnd.nextInt(N);
      if (i == j) continue;

      long delta = deltaEnergySwap(pos2tile, rows, cols, i, j, diffH, diffV);
      if (delta <= 0 || Math.random() < fast2Pow(-delta / temperature)) {
        int tmp = pos2tile[i]; pos2tile[i] = pos2tile[j]; pos2tile[j] = tmp;
        energy += delta;
      }

      if ((it & 0x3FFF) == 0 && cb != null) {
        long now = System.currentTimeMillis();
        if (now >= nextTick) {
          cb.onStatus((now - startTime)/1000, t*100.0, it, temperature, energy);
          nextTick = now + 300;
        }
      }
    }
    return pos2tile;
  }

  /** Bangun bitmap hasil dari permutasi 0-based (panjang rows*cols), pos row-major. */
  public static Bitmap reconstruct(Bitmap src, int rows, int cols, int[] perm0) {
    if (rows <= 0 || cols <= 0) throw new IllegalArgumentException("rows/cols harus > 0");
    if (perm0 == null || perm0.length != rows*cols) throw new IllegalArgumentException("perm length mismatch");
    final int W = src.getWidth(), H = src.getHeight();
    final int tileW = Math.max(1, W / cols);
    final int tileH = Math.max(1, H / rows);

    Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        int pos = r * cols + c;
        int tileId = perm0[pos]; // 0-based: id asal
        int tr = tileId / cols, tc = tileId % cols;

        int sx = tc * tileW;
        int sy = tr * tileH;
        int dx = c * tileW;
        int dy = r * tileH;

        int wCopy = Math.min(tileW, W - Math.max(sx, dx));
        int hCopy = Math.min(tileH, H - Math.max(sy, dy));
        if (wCopy <= 0 || hCopy <= 0) continue;

        int[] line = new int[wCopy];
        for (int y = 0; y < hCopy; y++) {
          src.getPixels(line, 0, wCopy, sx, sy + y, wCopy, 1);
          out.setPixels(line, 0, wCopy, dx, dy + y, wCopy, 1);
        }
      }
    }
    return out;
  }

  /** Bangun bitmap hasil + overlay angka 1-based di pusat tiap tile (kontras). */
  public static Bitmap reconstructWithNumbers(Bitmap src, int rows, int cols, int[] perm0) {
    Bitmap base = reconstruct(src, rows, cols, perm0);
    Bitmap mutable = base.copy(Bitmap.Config.ARGB_8888, true);
    Canvas canvas = new Canvas(mutable);

    int W = mutable.getWidth(), H = mutable.getHeight();
    int tileW = Math.max(1, W / cols);
    int tileH = Math.max(1, H / rows);

    // Ukuran teks adaptif
    float textSize = Math.max(24f, Math.min(tileW, tileH) / 2.5f);

    Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
    circle.setStyle(Paint.Style.FILL);
    circle.setColor(0xAA000000); // hitam semi-transparan

    Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    text.setTextAlign(Paint.Align.CENTER);
    text.setTextSize(textSize);
    text.setColor(0xFFFFFFFF); // putih

    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        int pos = r * cols + c;
        int id1 = perm0[pos] + 1;      // tampilkan 1-based
        float cx = c * tileW + tileW / 2f;
        float cy = r * tileH + tileH / 2f;

        float rad = Math.min(tileW, tileH) * 0.35f;
        canvas.drawCircle(cx, cy - rad*0.1f, rad, circle);
        canvas.drawText(String.valueOf(id1), cx, cy + textSize * 0.35f, text);
      }
    }
    return mutable;
  }

  // ===== helper SA / cost =====

  private static long totalEnergy(int[] pos2tile, int rows, int cols, int[][] diffH, int[][] diffV) {
    long e = 0;
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        int p = r*cols + c;
        int a = pos2tile[p];
        if (c + 1 < cols) e += diffH[a][pos2tile[p+1]];
        if (r + 1 < rows) e += diffV[a][pos2tile[p+cols]];
      }
    }
    return e;
  }

  private static long deltaEnergySwap(int[] pos2tile, int rows, int cols, int i, int j,
                                      int[][] diffH, int[][] diffV) {
    if (i == j) return 0;
    if (j < i) { int t = i; i = j; j = t; }
    long before = localEnergyAt(pos2tile, rows, cols, i, diffH, diffV)
                + localEnergyAt(pos2tile, rows, cols, j, diffH, diffV);
    int ai = pos2tile[i], aj = pos2tile[j];
    pos2tile[i] = aj; pos2tile[j] = ai;
    long after = localEnergyAt(pos2tile, rows, cols, i, diffH, diffV)
               + localEnergyAt(pos2tile, rows, cols, j, diffH, diffV);
    pos2tile[i] = ai; pos2tile[j] = aj;
    return after - before;
  }

  private static long localEnergyAt(int[] pos2tile, int rows, int cols, int p,
                                    int[][] diffH, int[][] diffV) {
    int r = p / cols, c = p % cols;
    int a = pos2tile[p];
    long e = 0;
    if (c - 1 >= 0) e += diffH[pos2tile[p - 1]][a];
    if (c + 1 < cols) e += diffH[a][pos2tile[p + 1]];
    if (r - 1 >= 0) e += diffV[pos2tile[p - cols]][a];
    if (r + 1 < rows) e += diffV[a][pos2tile[p + cols]];
    return e;
  }

  private static int edgeDiff(int[] px, int W, int H,
                              int ar, int ac, int br, int bc,
                              int tileW, int tileH, boolean horizontal) {
    int ax = ac * tileW;
    int ay = ar * tileH;
    int bx = bc * tileW;
    int by = br * tileH;
    int diff = 0;

    if (horizontal) {
      int xA = Math.min(W - 1, ax + tileW - 1);
      int xB = bx;
      int hh = Math.min(Math.min(tileH, H - ay), Math.min(tileH, H - by));
      for (int k = 0; k < hh; k++) {
        int pA = px[(ay + k) * W + xA];
        int pB = px[(by + k) * W + xB];
        diff += rgbAbsDiff(pA, pB);
      }
    } else {
      int yA = Math.min(H - 1, ay + tileH - 1);
      int yB = by;
      int ww = Math.min(Math.min(tileW, W - ax), Math.min(tileW, W - bx));
      for (int k = 0; k < ww; k++) {
        int pA = px[yA * W + (ax + k)];
        int pB = px[yB * W + (bx + k)];
        diff += rgbAbsDiff(pA, pB);
      }
    }
    return diff;
  }

  private static int rgbAbsDiff(int p0, int p1) {
    int d = 0;
    for (int i = 0; i < 3; i++) {
      d += Math.abs(((p0 >>> (i * 8)) & 0xFF) - ((p1 >>> (i * 8)) & 0xFF));
    }
    return d;
  }

  private static double fast2Pow(double x) {
    if (x < -1022) return 0;
    if (x >= 1024) return Double.POSITIVE_INFINITY;
    double y = Math.floor(x);
    double z = x - y;
    long bits = ((long)((int)y + 1023)) << 52;
    double u = Double.longBitsToDouble(bits);
    double v = ((0.07901988694851841 * z + 0.22412622970387342) * z + 0.6968388359765078) * z + 0.9998119079289554;
    return u * v;
  }
}

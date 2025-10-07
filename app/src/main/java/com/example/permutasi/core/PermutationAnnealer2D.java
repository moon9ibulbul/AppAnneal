package com.example.permutasi.core;

import android.graphics.Bitmap;
import java.util.Arrays;
import java.util.Random;

/**
 * Annealing 2D tile-permutation untuk gambar yang dipotong menjadi grid rows x cols.
 *
 * Konvensi:
 * - Gambar dipotong jadi N = rows*cols tile (row-major, ID asal 0..N-1).
 * - Kita menyusun kembali tile di grid keluaran (row-major posisi 0..N-1).
 * - Keluaran: perm[k] = ID tile asal yang diletakkan pada posisi k (row-major).
 *
 * Cost:
 * - Jumlah perbedaan tepi horizontal (kanan) dan vertikal (bawah) antar tile yang bertetangga.
 * - Makin kecil cost -> makin “nyambung”.
 *
 * Move:
 * - Swap dua posisi acak dalam konfigurasi, evaluasi delta-cost lokal (hitung ulang di tepi-tepi terpengaruh).
 */
public final class PermutationAnnealer2D {

  public interface ProgressListener {
    void onStatus(long seconds, double donePct, long iter, double temperature, long energy);
  }

  private PermutationAnnealer2D() {}

  public static int[] solve(Bitmap src, int rows, int cols, long iterations, double startTemp,
                            ProgressListener cb) {
    if (rows <= 0 || cols <= 0) throw new IllegalArgumentException("rows/cols harus > 0");
    if (iterations <= 0) throw new IllegalArgumentException("iterations harus > 0");
    if (startTemp <= 0) throw new IllegalArgumentException("startTemp harus > 0");

    final int W = src.getWidth(), H = src.getHeight();
    final int tileW = Math.max(1, W / cols);
    final int tileH = Math.max(1, H / rows);
    final int N = rows * cols;

    // Ekstrak piksel seluruh gambar
    int[] pixels = new int[W * H];
    src.getPixels(pixels, 0, W, 0, 0, W, H);

    // Precompute tepi setiap tile (kanan & bawah) untuk perbandingan cepat
    // Kita butuh fungsi diff horizontal (antara tepi kanan A dan tepi kiri B),
    // dan diff vertikal (antara tepi bawah A dan tepi atas B).
    // Untuk mempercepat, kita buat cache diff antar pasangan tile utk arah H dan V.
    int[][] diffH = new int[N][N]; // cost jika A di kiri B (A->B)
    int[][] diffV = new int[N][N]; // cost jika A di atas B (A->B)

    for (int a = 0; a < N; a++) {
      int ar = a / cols, ac = a % cols;
      for (int b = 0; b < N; b++) {
        int br = b / cols, bc = b % cols;
        diffH[a][b] = edgeDiffVerticalStrip(pixels, W, H, ar, ac, br, bc, tileW, tileH, /*horizontal=*/true);
        diffV[a][b] = edgeDiffVerticalStrip(pixels, W, H, ar, ac, br, bc, tileW, tileH, /*horizontal=*/false);
      }
    }

    // Permutasi awal: identitas
    int[] pos2tile = new int[N];
    for (int i = 0; i < N; i++) pos2tile[i] = i;

    // Hitung energi awal (jumlah cost semua pasangan tetangga)
    long energy = totalEnergy(pos2tile, rows, cols, diffH, diffV);

    Random rnd = new Random();
    long startTime = System.currentTimeMillis();
    long nextTick = startTime;

    // Annealing (swap 2 posisi)
    for (long it = 0; it < iterations; it++) {
      double t = (double) it / (double) iterations;
      double temperature = (1.0 - t) * startTemp;

      int i = rnd.nextInt(N);
      int j = rnd.nextInt(N);
      if (i == j) continue;

      long delta = deltaEnergySwap(pos2tile, rows, cols, i, j, diffH, diffV);
      if (delta <= 0 || Math.random() < fast2Pow(-delta / temperature)) {
        // terima swap
        int tmp = pos2tile[i]; pos2tile[i] = pos2tile[j]; pos2tile[j] = tmp;
        energy += delta;
      }

      if ((it & 0x3FFF) == 0 && cb != null) {
        long now = System.currentTimeMillis();
        if (now >= nextTick) {
          cb.onStatus((now - startTime)/1000, t*100.0, it, temperature, energy);
          nextTick = now + 300; // update ~3x/detik
        }
      }
    }

    return pos2tile;
  }

  // Hitung cost total dari konfigurasi
  private static long totalEnergy(int[] pos2tile, int rows, int cols,
                                  int[][] diffH, int[][] diffV) {
    long e = 0;
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        int p = r*cols + c;
        int a = pos2tile[p];
        if (c + 1 < cols) {
          int b = pos2tile[p+1];
          e += diffH[a][b];
        }
        if (r + 1 < rows) {
          int b = pos2tile[p+cols];
          e += diffV[a][b];
        }
      }
    }
    return e;
  }

  // Hitung perubahan energi jika swap posisi i dan j
  private static long deltaEnergySwap(int[] pos2tile, int rows, int cols, int i, int j,
                                      int[][] diffH, int[][] diffV) {
    if (i == j) return 0;
    if (j < i) { int t = i; i = j; j = t; }

    // Pos tetangga yang terdampak adalah i dan j beserta kiri/kanan/atas/bawah masing-masing.
    // Kita hitung energi lokal sebelum & sesudah.
    long before = localEnergyAt(pos2tile, rows, cols, i, diffH, diffV)
                + localEnergyAt(pos2tile, rows, cols, j, diffH, diffV);
    // swap
    int ai = pos2tile[i], aj = pos2tile[j];
    pos2tile[i] = aj; pos2tile[j] = ai;
    long after = localEnergyAt(pos2tile, rows, cols, i, diffH, diffV)
               + localEnergyAt(pos2tile, rows, cols, j, diffH, diffV);
    // revert
    pos2tile[i] = ai; pos2tile[j] = aj;
    return after - before;
  }

  // Energi lokal di sekitar posisi p (hanya edge yang melibatkan p)
  private static long localEnergyAt(int[] pos2tile, int rows, int cols, int p,
                                    int[][] diffH, int[][] diffV) {
    int r = p / cols, c = p % cols;
    int a = pos2tile[p];
    long e = 0;
    // kiri
    if (c - 1 >= 0) {
      int left = pos2tile[p - 1];
      e += diffH[left][a];
    }
    // kanan
    if (c + 1 < cols) {
      int right = pos2tile[p + 1];
      e += diffH[a][right];
    }
    // atas
    if (r - 1 >= 0) {
      int up = pos2tile[p - cols];
      e += diffV[up][a];
    }
    // bawah
    if (r + 1 < rows) {
      int down = pos2tile[p + cols];
      e += diffV[a][down];
    }
    return e;
  }

  // Hitung perbedaan di sepanjang strip tepi (vertikal untuk horizontal-join, horizontal untuk vertical-join)
  private static int edgeDiffVerticalStrip(int[] px, int W, int H,
                                           int ar, int ac, int br, int bc,
                                           int tileW, int tileH, boolean horizontal) {
    int ax = ac * tileW;
    int ay = ar * tileH;
    int bx = bc * tileW;
    int by = br * tileH;
    int diff = 0;

    if (horizontal) {
      // bandingkan tepi kanan tile A dengan tepi kiri tile B
      int xA = Math.min(W - 1, ax + tileW - 1);
      int xB = bx;
      int h = Math.min(tileH, H - ay);
      int h2 = Math.min(tileH, H - by);
      int hh = Math.min(h, h2);
      for (int k = 0; k < hh; k++) {
        int yA = ay + k;
        int yB = by + k;
        int pA = px[yA * W + xA];
        int pB = px[yB * W + xB];
        diff += rgbAbsDiff(pA, pB);
      }
    } else {
      // bandingkan tepi bawah tile A dengan tepi atas tile B
      int yA = Math.min(H - 1, ay + tileH - 1);
      int yB = by;
      int w = Math.min(tileW, W - ax);
      int w2 = Math.min(tileW, W - bx);
      int ww = Math.min(w, w2);
      for (int k = 0; k < ww; k++) {
        int xA = ax + k;
        int xB = bx + k;
        int pA = px[yA * W + xA];
        int pB = px[yB * W + xB];
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

  // 2^x approx untuk probabilitas SA
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

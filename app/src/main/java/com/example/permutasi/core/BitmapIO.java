package com.example.permutasi.core;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

public final class BitmapIO {
  private BitmapIO() {}

  public static Bitmap read(ContentResolver cr, Uri uri) throws IOException {
    try (InputStream in = cr.openInputStream(uri)) {
      if (in == null) throw new IOException("Tidak bisa buka stream: " + uri);
      Bitmap src = BitmapFactory.decodeStream(in);
      if (src == null) throw new IOException("Gambar rusak/tidak didukung: " + uri);
      return src.copy(Bitmap.Config.ARGB_8888, true);
    }
  }
}

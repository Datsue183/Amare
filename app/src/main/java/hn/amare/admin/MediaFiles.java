package hn.amare.admin;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import java.util.UUID;

public final class MediaFiles {
  private final Context context;

  public MediaFiles(Context c) {
    context = c;
  }

  public File directory() {
    File d = new File(context.getFilesDir(), "images");
    if (!d.exists() && !d.mkdirs())
      throw new IllegalStateException("No se pudo crear la carpeta de imágenes.");
    return d;
  }

  public File file(String name) {
    if (name == null || !name.matches("[a-zA-Z0-9_-]+\\.jpg"))
      throw new IllegalArgumentException("Imagen inválida.");
    return new File(directory(), name);
  }

  public String importImage(Uri uri) throws IOException {
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    try (InputStream in = context.getContentResolver().openInputStream(uri)) {
      BitmapFactory.decodeStream(in, null, bounds);
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
      throw new IOException("El archivo no es una imagen compatible. Usa JPG o PNG.");
    BitmapFactory.Options opts = new BitmapFactory.Options();
    opts.inSampleSize = 1;
    while (bounds.outWidth / opts.inSampleSize > 2048
        || bounds.outHeight / opts.inSampleSize > 2048) opts.inSampleSize *= 2;
    Bitmap decoded;
    try (InputStream in = context.getContentResolver().openInputStream(uri)) {
      decoded = BitmapFactory.decodeStream(in, null, opts);
    }
    if (decoded == null) throw new IOException("No se pudo leer la imagen.");
    // Phone cameras may keep orientation in EXIF rather than rotating their pixel data.
    int orientation = ExifInterface.ORIENTATION_NORMAL;
    try (InputStream in = context.getContentResolver().openInputStream(uri)) {
      orientation =
          new ExifInterface(in)
              .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
    } catch (IOException ignored) {
      /* Images without EXIF still import normally. */
    }
    Matrix transform = new Matrix();
    switch (orientation) {
      case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
        transform.setScale(-1, 1);
        break;
      case ExifInterface.ORIENTATION_ROTATE_180:
        transform.setRotate(180);
        break;
      case ExifInterface.ORIENTATION_FLIP_VERTICAL:
        transform.setScale(1, -1);
        break;
      case ExifInterface.ORIENTATION_TRANSPOSE:
        transform.setRotate(90);
        transform.postScale(-1, 1);
        break;
      case ExifInterface.ORIENTATION_ROTATE_90:
        transform.setRotate(90);
        break;
      case ExifInterface.ORIENTATION_TRANSVERSE:
        transform.setRotate(270);
        transform.postScale(-1, 1);
        break;
      case ExifInterface.ORIENTATION_ROTATE_270:
        transform.setRotate(270);
        break;
      default:
        break;
    }
    if (!transform.isIdentity()) {
      Bitmap oriented =
          Bitmap.createBitmap(
              decoded, 0, 0, decoded.getWidth(), decoded.getHeight(), transform, true);
      if (oriented != decoded) decoded.recycle();
      decoded = oriented;
    }
    float ratio = Math.min(1f, 1600f / Math.max(decoded.getWidth(), decoded.getHeight()));
    Bitmap resized =
        ratio < 1
            ? Bitmap.createScaledBitmap(
                decoded,
                Math.max(1, Math.round(decoded.getWidth() * ratio)),
                Math.max(1, Math.round(decoded.getHeight() * ratio)),
                true)
            : decoded;
    String name = UUID.randomUUID() + ".jpg";
    File dest = file(name);
    try (OutputStream out = new FileOutputStream(dest)) {
      if (!resized.compress(Bitmap.CompressFormat.JPEG, 88, out))
        throw new IOException("No se pudo guardar la imagen.");
    } catch (IOException e) {
      dest.delete();
      throw e;
    } finally {
      if (resized != decoded) resized.recycle();
      decoded.recycle();
    }
    return name;
  }

  public Bitmap thumbnail(String name, int max) {
    if (name == null || name.isEmpty()) return null;
    File f = file(name);
    BitmapFactory.Options o = new BitmapFactory.Options();
    o.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(f.getPath(), o);
    o.inSampleSize = 1;
    while (Math.max(o.outWidth, o.outHeight) / o.inSampleSize > max) o.inSampleSize *= 2;
    o.inJustDecodeBounds = false;
    return BitmapFactory.decodeFile(f.getPath(), o);
  }

  public File shareImage(String name) throws IOException {
    File dir = new File(context.getCacheDir(), "share");
    if (!dir.exists() && !dir.mkdirs()) throw new IOException("No se pudo preparar la imagen.");
    // Only disposable catalog photos enter this directory; no database or backup is exposed.
    File f = new File(dir, UUID.randomUUID() + ".jpg");
    copy(file(name), f);
    return f;
  }

  static void copy(File source, File dest) throws IOException {
    try (InputStream in = new FileInputStream(source);
        OutputStream out = new FileOutputStream(dest)) {
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) out.write(b, 0, n);
    }
  }
}

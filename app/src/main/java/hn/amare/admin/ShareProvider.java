package hn.amare.admin;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

public final class ShareProvider extends ContentProvider {
  @Override
  public boolean onCreate() {
    return true;
  }

  private File resolve(Uri uri) throws FileNotFoundException {
    if (uri.getPathSegments().size() != 1) throw new FileNotFoundException();
    String name = uri.getLastPathSegment();
    if (name == null || !name.matches("[a-zA-Z0-9_-]+\\.jpg")) throw new FileNotFoundException();
    File f = new File(new File(getContext().getCacheDir(), "share"), name);
    if (!f.isFile()) throw new FileNotFoundException();
    return f;
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    if (!"r".equals(mode)) throw new FileNotFoundException("Solo lectura");
    return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
  }

  @Override
  public String getType(Uri uri) {
    return "image/jpeg";
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
    String[] cols =
        projection == null
            ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
            : projection;
    MatrixCursor c = new MatrixCursor(cols);
    try {
      File f = resolve(uri);
      Object[] row = new Object[cols.length];
      for (int i = 0; i < cols.length; i++)
        row[i] =
            OpenableColumns.DISPLAY_NAME.equals(cols[i])
                ? f.getName()
                : OpenableColumns.SIZE.equals(cols[i]) ? f.length() : null;
      c.addRow(row);
    } catch (FileNotFoundException ignored) {
    }
    return c;
  }

  @Override
  public Uri insert(Uri uri, ContentValues v) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int delete(Uri uri, String s, String[] args) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int update(Uri uri, ContentValues v, String s, String[] args) {
    throw new UnsupportedOperationException();
  }
}

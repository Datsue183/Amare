package hn.amare.admin;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MediaFilesTest {
  private File image(int width, int height) throws Exception {
    File f =
        File.createTempFile("photo", ".jpg", RuntimeEnvironment.getApplication().getCacheDir());
    Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    try (OutputStream out = new FileOutputStream(f)) {
      assertTrue(b.compress(Bitmap.CompressFormat.JPEG, 90, out));
    }
    b.recycle();
    return f;
  }

  @Test
  public void importedPhotoIsPrivateAndSurvivesSourceDeletion() throws Exception {
    MediaFiles media = new MediaFiles(RuntimeEnvironment.getApplication());
    File source = image(100, 200);
    String name = media.importImage(Uri.fromFile(source));
    assertTrue(source.delete());
    Bitmap b = BitmapFactory.decodeFile(media.file(name).getPath());
    assertEquals(100, b.getWidth());
    assertEquals(200, b.getHeight());
    b.recycle();
  }

  @Test
  public void largePhotosAreResizedForLowMemoryDevices() throws Exception {
    MediaFiles media = new MediaFiles(RuntimeEnvironment.getApplication());
    File source = image(3000, 2000);
    String name = media.importImage(Uri.fromFile(source));
    Bitmap b = BitmapFactory.decodeFile(media.file(name).getPath());
    assertTrue(b.getWidth() <= 1600);
    assertTrue(b.getHeight() <= 1600);
    b.recycle();
    source.delete();
  }

  @Test
  public void cameraExifOrientationIsApplied() throws Exception {
    File source = image(100, 200);
    ExifInterface exif = new ExifInterface(source.getPath());
    exif.setAttribute(
        ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_ROTATE_90));
    exif.saveAttributes();
    MediaFiles media = new MediaFiles(RuntimeEnvironment.getApplication());
    String name = media.importImage(Uri.fromFile(source));
    Bitmap b = BitmapFactory.decodeFile(media.file(name).getPath());
    assertEquals(200, b.getWidth());
    assertEquals(100, b.getHeight());
    b.recycle();
    source.delete();
  }

  @Test
  public void corruptImageIsRejected() throws Exception {
    File f =
        File.createTempFile("bad-photo", ".jpg", RuntimeEnvironment.getApplication().getCacheDir());
    try (OutputStream out = new FileOutputStream(f)) {
      out.write("not an image".getBytes());
    }
    MediaFiles media = new MediaFiles(RuntimeEnvironment.getApplication());
    assertThrows(IOException.class, () -> media.importImage(Uri.fromFile(f)));
    f.delete();
  }
}

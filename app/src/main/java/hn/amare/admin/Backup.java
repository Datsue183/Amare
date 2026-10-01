package hn.amare.admin;

import android.graphics.BitmapFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Backup {
  private static final long MAX_BYTES = 256L * 1024 * 1024;

  private Backup() {}

  public static void write(LocalStore store, MediaFiles media, OutputStream output)
      throws Exception {
    JSONObject data = store.exportData();
    try (ZipOutputStream zip = new ZipOutputStream(output)) {
      zip.putNextEntry(new ZipEntry("data.json"));
      zip.write(data.toString().getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
      Set<String> names = new HashSet<>();
      JSONArray p = data.getJSONObject("tables").getJSONArray("products");
      for (int i = 0; i < p.length(); i++) {
        String name = p.getJSONObject(i).getString("image");
        if (!name.isEmpty()) names.add(name);
      }
      byte[] b = new byte[8192];
      for (String name : names) {
        zip.putNextEntry(new ZipEntry("images/" + name));
        try (InputStream in = new FileInputStream(media.file(name))) {
          int n;
          while ((n = in.read(b)) != -1) zip.write(b, 0, n);
        }
        zip.closeEntry();
      }
    }
  }

  public static void restore(LocalStore store, MediaFiles media, InputStream input, File cache)
      throws Exception {
    File stage = new File(cache, "restore-" + UUID.randomUUID());
    if (!stage.mkdirs()) throw new IOException("No se pudo preparar el respaldo.");
    List<File> copied = new ArrayList<>();
    boolean imported = false;
    try {
      Set<String> seen = new HashSet<>();
      long total = 0;
      try (ZipInputStream zip = new ZipInputStream(input)) {
        ZipEntry e;
        byte[] b = new byte[8192];
        int entries = 0;
        while ((e = zip.getNextEntry()) != null) {
          String name = e.getName();
          if (++entries > 10001
              || (!name.equals("data.json") && !name.matches("images/[a-zA-Z0-9_-]+\\.jpg"))
              || !seen.add(name)
              || e.isDirectory()) throw new IOException("Contenido de respaldo inválido.");
          File dest = new File(stage, name.replace("images/", ""));
          long entrySize = 0;
          try (OutputStream out = new FileOutputStream(dest)) {
            int n;
            while ((n = zip.read(b)) != -1) {
              total += n;
              entrySize += n;
              if (total > MAX_BYTES
                  || entrySize > (name.equals("data.json") ? 32L : 20L) * 1024 * 1024)
                throw new IOException("El respaldo supera el tamaño permitido.");
              out.write(b, 0, n);
            }
          }
        }
      }
      File json = new File(stage, "data.json");
      if (!json.isFile()) throw new IOException("Faltan los datos del respaldo.");
      JSONObject data =
          new JSONObject(
              new String(java.nio.file.Files.readAllBytes(json.toPath()), StandardCharsets.UTF_8));
      JSONArray products = data.getJSONObject("tables").getJSONArray("products");
      Map<String, String> renamed = new HashMap<>();
      for (int i = 0; i < products.length(); i++) {
        JSONObject p = products.getJSONObject(i);
        String old = p.getString("image");
        if (old.isEmpty()) continue;
        if (!old.matches("[a-zA-Z0-9_-]+\\.jpg")) throw new IOException("Imagen inválida.");
        if (!renamed.containsKey(old)) {
          File src = new File(stage, old);
          BitmapFactory.Options o = new BitmapFactory.Options();
          o.inJustDecodeBounds = true;
          BitmapFactory.decodeFile(src.getPath(), o);
          if (o.outWidth <= 0 || o.outHeight <= 0 || o.outWidth > 4096 || o.outHeight > 4096)
            throw new IOException("Falta una imagen o está dañada.");
          String next = UUID.randomUUID() + ".jpg";
          File dest = media.file(next);
          copied.add(dest);
          MediaFiles.copy(src, dest);
          renamed.put(old, next);
        }
        p.put("image", renamed.get(old));
      }
      // Files use fresh names; transaction rollback cannot damage current photos.
      store.importData(data);
      imported = true;
    } finally {
      if (!imported) for (File f : copied) f.delete();
      File[] files = stage.listFiles();
      if (files != null) for (File f : files) f.delete();
      stage.delete();
    }
  }
}

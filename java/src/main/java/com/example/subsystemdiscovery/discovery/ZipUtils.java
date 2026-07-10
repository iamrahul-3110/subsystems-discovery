package com.example.subsystemdiscovery.discovery;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipUtils {

    public static byte[] zipString(String str, String entryName) throws IOException {
        if (str == null) {
            return null;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            zos.write(str.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    public static String unzipString(byte[] compressedBytes) throws IOException {
        if (compressedBytes == null) {
            return null;
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(compressedBytes);
        try (ZipInputStream zis = new ZipInputStream(bais)) {
            ZipEntry entry = zis.getNextEntry();
            if (entry != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
                return baos.toString(StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}

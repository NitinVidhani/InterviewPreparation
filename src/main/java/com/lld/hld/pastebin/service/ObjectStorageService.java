package com.lld.hld.pastebin.service;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Object Storage Service — simulates AWS S3.
 *
 * In production: AWS S3 / Google Cloud Storage / Azure Blob Storage
 *
 * S3 Bucket: "pastebin-content-prod"
 * Object key pattern: "pastes/{paste_key}.txt"
 *
 * Key responsibilities:
 * - Store paste content as compressed (GZIP) objects
 * - Retrieve and decompress content on read
 * - Delete objects when pastes expire or are removed
 *
 * GZIP compression typically saves ~70% for text content:
 * Original: 10 KB → Compressed: ~3 KB
 * Original: 1 MB → Compressed: ~300 KB
 */
public class ObjectStorageService {

    // Simulates S3 bucket: objectKey → compressed content bytes
    private final Map<String, byte[]> bucket = new ConcurrentHashMap<>();

    /**
     * Upload content to S3.
     *
     * @param objectKey e.g., "pastes/aB3xY9.txt"
     * @param content   raw text content
     * @return size of compressed content in bytes
     */
    public long upload(String objectKey, String content) {
        byte[] compressed = compress(content);
        bucket.put(objectKey, compressed);

        long originalSize = content.getBytes(StandardCharsets.UTF_8).length;
        double ratio = (1.0 - (double) compressed.length / originalSize) * 100;

        System.out.printf("[S3] PUT  key=%s | original=%dB | compressed=%dB | savings=%.0f%%%n",
                objectKey, originalSize, compressed.length, ratio);

        return compressed.length;
    }

    /**
     * Download content from S3.
     *
     * @param objectKey e.g., "pastes/aB3xY9.txt"
     * @return decompressed text content, or null if not found
     */
    public String download(String objectKey) {
        byte[] compressed = bucket.get(objectKey);
        if (compressed == null) {
            System.out.printf("[S3] GET  key=%s → NOT FOUND%n", objectKey);
            return null;
        }
        String content = decompress(compressed);
        System.out.printf("[S3] GET  key=%s → %d bytes%n", objectKey, content.length());
        return content;
    }

    /**
     * Delete an object from S3.
     *
     * @param objectKey e.g., "pastes/aB3xY9.txt"
     * @return true if object existed and was deleted
     */
    public boolean delete(String objectKey) {
        boolean existed = bucket.remove(objectKey) != null;
        System.out.printf("[S3] DELETE key=%s → %s%n", objectKey, existed ? "deleted" : "not found");
        return existed;
    }

    /** Check if an object exists in S3 */
    public boolean exists(String objectKey) {
        return bucket.containsKey(objectKey);
    }

    /** Total number of objects in the bucket */
    public int objectCount() {
        return bucket.size();
    }

    // ── GZIP Compression ─────────────────────────────────────────────────────

    private byte[] compress(String content) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(bos);
            gzip.write(content.getBytes(StandardCharsets.UTF_8));
            gzip.close();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("GZIP compression failed", e);
        }
    }

    private String decompress(byte[] compressed) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
            GZIPInputStream gzip = new GZIPInputStream(bis);
            byte[] decompressed = gzip.readAllBytes();
            gzip.close();
            return new String(decompressed, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("GZIP decompression failed", e);
        }
    }
}

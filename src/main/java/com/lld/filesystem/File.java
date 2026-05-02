package com.lld.filesystem;

import java.time.LocalDateTime;

/**
 * ══════════════════════════════════════════════════════════════════════
 * DESIGN PATTERN: Composite (Leaf)
 * ══════════════════════════════════════════════════════════════════════
 * File is a LEAF node in the Composite pattern.
 *
 * It has NO children — only content (data). It represents the end of
 * a branch in the file system tree.
 *
 * Properties:
 * - name: "resume.txt"
 * - content: "John Doe\nSoftware Engineer\n..."
 * - parent: reference to the containing Directory
 *
 * Thread safety note:
 * read() and write() are synchronized to prevent concurrent
 * read/write conflicts. In production, you'd use a ReadWriteLock
 * to allow multiple concurrent readers.
 * ══════════════════════════════════════════════════════════════════════
 */
public class File extends FileSystemEntry {

    private String content;

    public File(String name, Directory parent) {
        super(name, parent);
        this.content = ""; // empty file by default
    }

    /**
     * Read the file content.
     * synchronized → prevents reading while another thread is writing.
     */
    public synchronized String read() {
        return content;
    }

    /**
     * Write content to the file (overwrites existing content).
     * synchronized → prevents two threads from writing simultaneously.
     */
    public synchronized void write(String content) {
        this.content = content;
        this.modifiedAt = LocalDateTime.now();
    }

    /**
     * Append content to the file.
     */
    public synchronized void append(String content) {
        this.content += content;
        this.modifiedAt = LocalDateTime.now();
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public int getSize() {
        return content.length();
    }
}

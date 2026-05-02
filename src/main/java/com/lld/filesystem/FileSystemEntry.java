package com.lld.filesystem;

import java.time.LocalDateTime;

/**
 * ══════════════════════════════════════════════════════════════════════
 * DESIGN PATTERN: Composite (Component)
 * ══════════════════════════════════════════════════════════════════════
 * FileSystemEntry is the COMPONENT in the Composite pattern.
 *
 * It defines the common interface for BOTH files and directories.
 * This allows the rest of the system (FileSystem, Directory) to treat
 * files and directories UNIFORMLY through this base class.
 *
 * WHY Composite?
 * A file system is a TREE:
 * - Directory = internal node (has children, which are FileSystemEntry)
 * - File = leaf node (has content, no children)
 *
 * Without Composite, you'd need separate code paths everywhere:
 * if (entry instanceof File) { ... }
 * else if (entry instanceof Directory) { ... }
 *
 * With Composite, you call entry.getName(), entry.getPath() — it works
 * regardless of whether it's a file or directory.
 *
 * INTERVIEW TIP: "How would you add file permissions?"
 * → Add a Permissions field HERE (in the base class).
 * Both files and directories inherit it automatically.
 * ══════════════════════════════════════════════════════════════════════
 */
public abstract class FileSystemEntry {

    protected String name;
    protected Directory parent; // null only for root "/"
    protected final LocalDateTime createdAt;
    protected LocalDateTime modifiedAt;

    protected FileSystemEntry(String name, Directory parent) {
        this.name = name;
        this.parent = parent;
        this.createdAt = LocalDateTime.now();
        this.modifiedAt = this.createdAt;
    }

    /** Get the entry name (e.g., "resume.txt" or "docs"). */
    public String getName() {
        return name;
    }

    /**
     * Compute the full absolute path by walking up the parent chain.
     *
     * Example: For a file "resume.txt" inside /home/user/docs:
     * resume.txt.parent = docs
     * docs.parent = user
     * user.parent = home
     * home.parent = / (root, parent is null)
     *
     * Walk up: resume.txt → docs → user → home → /
     * Reverse: / → home → user → docs → resume.txt
     * Result: /home/user/docs/resume.txt
     */
    public String getPath() {
        if (parent == null) {
            // Root directory
            return "/";
        }

        StringBuilder path = new StringBuilder();
        FileSystemEntry current = this;

        // Walk up the tree collecting names
        while (current.parent != null) {
            path.insert(0, "/" + current.name);
            current = current.parent;
        }

        return path.toString();
    }

    /** Is this entry a directory? Overridden by subclasses. */
    public abstract boolean isDirectory();

    /** Get the size (file = content length, directory = total children size). */
    public abstract int getSize();

    public Directory getParent() {
        return parent;
    }

    public void setParent(Directory parent) {
        this.parent = parent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getModifiedAt() {
        return modifiedAt;
    }

    @Override
    public String toString() {
        return (isDirectory() ? "[DIR]  " : "[FILE] ") + getPath();
    }
}

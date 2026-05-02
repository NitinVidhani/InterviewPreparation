package com.lld.filesystem;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ══════════════════════════════════════════════════════════════════════
 * DESIGN PATTERN: Composite (Composite Node)
 * ══════════════════════════════════════════════════════════════════════
 * Directory is the COMPOSITE node in the Composite pattern.
 *
 * It contains a collection of FileSystemEntry objects — which can be
 * either Files (leaves) or other Directories (sub-composites).
 * This recursive structure is what makes the tree possible.
 *
 * Internal storage:
 * children: Map<String, FileSystemEntry>
 * - Key = child name (e.g., "docs", "resume.txt")
 * - Value = the FileSystemEntry (File or Directory)
 *
 * Using a Map gives O(1) lookup by name — critical for path resolution
 * where we traverse: "/home/user/docs" →
 * root.get("home").get("user").get("docs")
 *
 * ══════════════════════════════════════════════════════════════════════
 * COMPOSITE PATTERN in action:
 *
 * Directory root = new Directory("/", null);
 * Directory home = new Directory("home", root);
 * File bashrc = new File(".bashrc", home);
 *
 * root.addEntry(home); // Directory contains Directory ← recursive
 * home.addEntry(bashrc); // Directory contains File ← leaf
 *
 * root.getSize(); // recursively sums all children sizes
 * ══════════════════════════════════════════════════════════════════════
 */
public class Directory extends FileSystemEntry {

    // Map<childName, childEntry> — O(1) lookup by name
    private final Map<String, FileSystemEntry> children;

    public Directory(String name, Directory parent) {
        super(name, parent);
        this.children = new LinkedHashMap<>(); // preserves insertion order
    }

    /**
     * Add a child entry (file or directory) to this directory.
     *
     * @throws IllegalArgumentException if an entry with the same name already
     *                                  exists
     */
    public void addEntry(FileSystemEntry entry) {
        if (children.containsKey(entry.getName())) {
            throw new IllegalArgumentException(
                    "Entry '" + entry.getName() + "' already exists in " + getPath());
        }
        entry.setParent(this);
        children.put(entry.getName(), entry);
        this.modifiedAt = LocalDateTime.now();
    }

    /**
     * Remove a child entry by name.
     *
     * @return the removed entry, or null if not found
     */
    public FileSystemEntry removeEntry(String name) {
        FileSystemEntry removed = children.remove(name);
        if (removed != null) {
            removed.setParent(null);
            this.modifiedAt = LocalDateTime.now();
        }
        return removed;
    }

    /**
     * Get a child entry by name — O(1) via HashMap.
     *
     * This is called during path resolution. For path "/home/user/docs":
     * root.getEntry("home") → returns home Directory
     * home.getEntry("user") → returns user Directory
     * user.getEntry("docs") → returns docs Directory
     */
    public FileSystemEntry getEntry(String name) {
        return children.get(name);
    }

    /** Check if a child with the given name exists. */
    public boolean hasEntry(String name) {
        return children.containsKey(name);
    }

    /**
     * List all children, sorted alphabetically.
     * This is what `ls` returns.
     */
    public List<FileSystemEntry> getChildren() {
        return children.values()
                .stream()
                .sorted(Comparator.comparing(FileSystemEntry::getName))
                .collect(Collectors.toList());
    }

    /** Get only the names of children (for display). */
    public List<String> listChildNames() {
        return getChildren().stream()
                .map(e -> e.getName() + (e.isDirectory() ? "/" : ""))
                .collect(Collectors.toList());
    }

    /** Check if directory is empty. */
    public boolean isEmpty() {
        return children.isEmpty();
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    /**
     * Get total size of all contents recursively.
     *
     * COMPOSITE PATTERN: getSize() on a Directory recursively calls
     * getSize() on each child — which could be a File (returns content
     * length) or another Directory (recurses further).
     */
    @Override
    public int getSize() {
        int totalSize = 0;
        for (FileSystemEntry entry : children.values()) {
            totalSize += entry.getSize(); // polymorphic call — works for File or Directory
        }
        return totalSize;
    }

    /** Count total entries recursively (files + directories). */
    public int countAll() {
        int count = children.size();
        for (FileSystemEntry entry : children.values()) {
            if (entry.isDirectory()) {
                count += ((Directory) entry).countAll();
            }
        }
        return count;
    }
}

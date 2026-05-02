package com.lld.filesystem;

import java.util.*;

/**
 * ══════════════════════════════════════════════════════════════════════
 * FILE SYSTEM — Core Orchestrator
 * ══════════════════════════════════════════════════════════════════════
 *
 * DESIGN PATTERNS:
 * 1. SINGLETON — only one file system instance exists.
 * 2. COMPOSITE — used underneath (File/Directory hierarchy).
 *
 * This class acts as a FACADE over the Composite tree structure.
 * Clients (the demo, CLI, etc.) interact with this class, never with
 * File/Directory directly. It handles:
 * - Path parsing and resolution (including `.`, `..`, relative paths)
 * - Validation and error handling
 * - Current working directory tracking
 *
 * ══════════════════════════════════════════════════════════════════════
 * PATH RESOLUTION ALGORITHM
 * ══════════════════════════════════════════════════════════════════════
 * Given: "/home/user/../admin/./config"
 *
 * 1. Split by "/": ["home", "user", "..", "admin", ".", "config"]
 * 2. Process each component using a STACK:
 * "home" → push → [home]
 * "user" → push → [home, user]
 * ".." → pop → [home] (go to parent)
 * "admin" → push → [home, admin]
 * "." → skip → [home, admin] (current dir, no-op)
 * "config" → push → [home, admin, config]
 * 3. Result: /home/admin/config
 *
 * Then traverse the tree from root following each component.
 * ══════════════════════════════════════════════════════════════════════
 */
public class FileSystem {

    // ──────────────────────────────────────────────────────────────────
    // SINGLETON: volatile instance + private constructor + getInstance()
    // ──────────────────────────────────────────────────────────────────
    private static volatile FileSystem instance;

    private final Directory root;
    private Directory currentDirectory;

    /** Private constructor — Singleton. Creates root "/". */
    private FileSystem() {
        this.root = new Directory("/", null);
        this.currentDirectory = root;
        System.out.println("[FileSystem] Initialized with root /");
    }

    /** SINGLETON: Double-Checked Locking. */
    public static FileSystem getInstance() {
        if (instance == null) {
            synchronized (FileSystem.class) {
                if (instance == null) {
                    instance = new FileSystem();
                }
            }
        }
        return instance;
    }

    /** Reset for testing. */
    public static void resetInstance() {
        instance = null;
    }

    // ══════════════════════════════════════════════════════════════════
    // CORE OPERATIONS
    // ══════════════════════════════════════════════════════════════════

    /**
     * mkdir — Create a directory at the given path.
     * Creates intermediate directories if they don't exist (like `mkdir -p`).
     *
     * Example: mkdir("/home/user/docs")
     * Creates: /home → /home/user → /home/user/docs
     *
     * ──────────────────────────────────────────────────────────────
     * ALGORITHM:
     * 1. Parse the path into components: ["home", "user", "docs"]
     * 2. Starting from root (or currentDir for relative paths):
     * - For each component, check if directory exists.
     * - If not, create it (Composite pattern: Directory.addEntry).
     * - Move into it and continue.
     * ──────────────────────────────────────────────────────────────
     */
    public Directory mkdir(String path) {
        String[] components = parsePath(path);
        Directory current = getStartDirectory(path);

        for (String component : components) {
            if (component.isEmpty() || component.equals("."))
                continue;
            if (component.equals("..")) {
                current = (current.getParent() != null) ? current.getParent() : current;
                continue;
            }

            FileSystemEntry entry = current.getEntry(component);
            if (entry == null) {
                // Directory doesn't exist — create it
                // COMPOSITE PATTERN: adding a Directory as a child of another Directory
                Directory newDir = new Directory(component, current);
                current.addEntry(newDir);
                current = newDir;
            } else if (entry.isDirectory()) {
                // Already exists — move into it
                current = (Directory) entry;
            } else {
                throw new IllegalArgumentException(
                        "Cannot create directory: '" + component + "' is a file at " + entry.getPath());
            }
        }

        System.out.println("[mkdir] Created: " + current.getPath());
        return current;
    }

    /**
     * touch — Create an empty file at the given path.
     * Parent directories must exist (or use mkdir first).
     */
    public File touch(String path) {
        String[] components = parsePath(path);
        if (components.length == 0) {
            throw new IllegalArgumentException("Invalid file path: " + path);
        }

        // Navigate to the parent directory
        String fileName = components[components.length - 1];
        Directory parent = navigateToParent(path);

        // Check if already exists
        FileSystemEntry existing = parent.getEntry(fileName);
        if (existing != null) {
            if (!existing.isDirectory()) {
                System.out.println("[touch] File already exists: " + existing.getPath());
                return (File) existing;
            }
            throw new IllegalArgumentException("A directory with name '" + fileName + "' already exists");
        }

        // COMPOSITE PATTERN: adding a File (leaf) as a child of Directory (composite)
        File file = new File(fileName, parent);
        parent.addEntry(file);
        System.out.println("[touch] Created: " + file.getPath());
        return file;
    }

    /**
     * write — Write content to a file. Creates the file if it doesn't exist.
     */
    public void write(String path, String content) {
        FileSystemEntry entry = resolve(path);
        if (entry == null) {
            // Auto-create: ensure parent dirs exist, then create file
            String[] components = parsePath(path);
            String fileName = components[components.length - 1];
            String parentPath = path.substring(0, path.lastIndexOf("/"));
            if (parentPath.isEmpty())
                parentPath = "/";
            mkdir(parentPath);
            entry = touch(path);
        }

        if (entry.isDirectory()) {
            throw new IllegalArgumentException("Cannot write to a directory: " + path);
        }

        ((File) entry).write(content);
        System.out.println("[write] Wrote " + content.length() + " chars to " + entry.getPath());
    }

    /**
     * read — Read the content of a file.
     */
    public String read(String path) {
        FileSystemEntry entry = resolve(path);
        if (entry == null) {
            throw new IllegalArgumentException("File not found: " + path);
        }
        if (entry.isDirectory()) {
            throw new IllegalArgumentException("Cannot read a directory: " + path);
        }

        String content = ((File) entry).read();
        System.out.printf("[read] %s → \"%s\"%n", entry.getPath(),
                content.length() > 50 ? content.substring(0, 50) + "..." : content);
        return content;
    }

    /**
     * ls — List contents of a directory (sorted), or the file name if path is a
     * file.
     *
     * @return sorted list of entry names (directories have trailing "/")
     */
    public List<String> ls(String path) {
        FileSystemEntry entry = resolve(path);
        if (entry == null) {
            throw new IllegalArgumentException("Path not found: " + path);
        }

        if (!entry.isDirectory()) {
            // If path is a file, return just the file name
            return List.of(entry.getName());
        }

        List<String> names = ((Directory) entry).listChildNames();
        System.out.println("[ls] " + entry.getPath() + " → " + names);
        return names;
    }

    /**
     * rm — Delete a file or directory (recursive).
     * Cannot delete the root directory.
     */
    public void rm(String path) {
        FileSystemEntry entry = resolve(path);
        if (entry == null) {
            throw new IllegalArgumentException("Path not found: " + path);
        }
        if (entry == root) {
            throw new IllegalArgumentException("Cannot delete root directory");
        }

        Directory parent = entry.getParent();
        parent.removeEntry(entry.getName());
        System.out.println("[rm] Deleted: " + path);
    }

    /**
     * find — Search for files/directories by name within a subtree.
     * Returns all matching paths (recursive DFS).
     */
    public List<String> find(String startPath, String searchName) {
        FileSystemEntry start = resolve(startPath);
        if (start == null || !start.isDirectory()) {
            throw new IllegalArgumentException("Invalid search directory: " + startPath);
        }

        List<String> results = new ArrayList<>();
        findRecursive((Directory) start, searchName, results);
        System.out.println("[find] '" + searchName + "' in " + startPath + " → " + results);
        return results;
    }

    /** Recursive DFS search helper. */
    private void findRecursive(Directory dir, String searchName, List<String> results) {
        for (FileSystemEntry entry : dir.getChildren()) {
            if (entry.getName().equals(searchName)) {
                results.add(entry.getPath());
            }
            if (entry.isDirectory()) {
                findRecursive((Directory) entry, searchName, results);
            }
        }
    }

    /**
     * cd — Change current working directory.
     * Supports: absolute paths (/home), relative paths (docs), "..", "."
     */
    public void cd(String path) {
        FileSystemEntry entry = resolve(path);
        if (entry == null) {
            throw new IllegalArgumentException("Directory not found: " + path);
        }
        if (!entry.isDirectory()) {
            throw new IllegalArgumentException("Not a directory: " + path);
        }

        currentDirectory = (Directory) entry;
        System.out.println("[cd] Changed to: " + pwd());
    }

    /**
     * pwd — Print the current working directory path.
     */
    public String pwd() {
        return currentDirectory.getPath();
    }

    /**
     * tree — Print a visual tree representation of the file system.
     * Great for debugging and demo purposes.
     */
    public void tree(String path) {
        FileSystemEntry entry = resolve(path);
        if (entry == null) {
            System.out.println("[tree] Path not found: " + path);
            return;
        }
        System.out.println("\n" + entry.getPath());
        if (entry.isDirectory()) {
            printTree((Directory) entry, "");
        }
    }

    /** Recursive tree printer with visual indentation. */
    private void printTree(Directory dir, String indent) {
        List<FileSystemEntry> children = dir.getChildren();
        for (int i = 0; i < children.size(); i++) {
            FileSystemEntry child = children.get(i);
            boolean isLast = (i == children.size() - 1);
            String connector = isLast ? "└── " : "├── ";
            String childIndent = isLast ? "    " : "│   ";

            if (child.isDirectory()) {
                System.out.println(indent + connector + child.getName() + "/");
                printTree((Directory) child, indent + childIndent);
            } else {
                System.out.printf("%s%s%s (%d bytes)%n",
                        indent, connector, child.getName(), child.getSize());
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // PATH RESOLUTION HELPERS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Resolve a path string to a FileSystemEntry.
     *
     * ──────────────────────────────────────────────────────────────
     * PATH RESOLUTION ALGORITHM:
     * 1. Determine start: absolute path → root, relative → currentDir
     * 2. Split path by "/"
     * 3. For each component:
     * "." → stay in current directory
     * ".." → go to parent (if exists)
     * name → look up child in current directory
     * 4. Return the final entry (or null if not found)
     *
     * This is the same algorithm used by real OS kernels (Linux VFS).
     * ──────────────────────────────────────────────────────────────
     */
    public FileSystemEntry resolve(String path) {
        if (path == null || path.isEmpty())
            return currentDirectory;
        if (path.equals("/"))
            return root;

        String[] components = parsePath(path);
        FileSystemEntry current = getStartDirectory(path);

        for (String component : components) {
            if (component.isEmpty() || component.equals("."))
                continue;

            if (component.equals("..")) {
                // Go to parent directory
                if (current instanceof Directory && ((Directory) current).getParent() != null) {
                    current = ((Directory) current).getParent();
                }
                continue;
            }

            if (!current.isDirectory()) {
                return null; // Can't traverse into a file
            }

            FileSystemEntry next = ((Directory) current).getEntry(component);
            if (next == null) {
                return null; // Path component not found
            }
            current = next;
        }

        return current;
    }

    /**
     * Determine the starting directory based on whether path is absolute or
     * relative.
     */
    private Directory getStartDirectory(String path) {
        return path.startsWith("/") ? root : currentDirectory;
    }

    /** Split a path into components, filtering empty strings. */
    private String[] parsePath(String path) {
        return Arrays.stream(path.split("/"))
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    /** Navigate to the parent directory of the given path. */
    private Directory navigateToParent(String path) {
        String[] components = parsePath(path);
        Directory current = getStartDirectory(path);

        // Navigate all components except the last one (which is the file/dir name)
        for (int i = 0; i < components.length - 1; i++) {
            String component = components[i];
            if (component.equals("."))
                continue;
            if (component.equals("..")) {
                current = (current.getParent() != null) ? current.getParent() : current;
                continue;
            }

            FileSystemEntry entry = current.getEntry(component);
            if (entry == null || !entry.isDirectory()) {
                throw new IllegalArgumentException(
                        "Parent directory not found: " + component + " in path " + path);
            }
            current = (Directory) entry;
        }

        return current;
    }

    // --- Getters ---
    public Directory getRoot() {
        return root;
    }

    public Directory getCurrentDirectory() {
        return currentDirectory;
    }
}

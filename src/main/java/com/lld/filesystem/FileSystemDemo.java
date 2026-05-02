package com.lld.filesystem;

/**
 * ══════════════════════════════════════════════════════════════════════
 * IN-MEMORY FILE SYSTEM — Full Demo
 * ══════════════════════════════════════════════════════════════════════
 *
 * This demo walks through every scenario an interviewer would ask about:
 *
 * 1. Creating directories (mkdir -p style)
 * 2. Creating and writing files
 * 3. Reading file content
 * 4. Listing directory contents (ls)
 * 5. Navigating with cd / pwd
 * 6. Path resolution with ".." and "."
 * 7. Finding files by name (recursive search)
 * 8. Deleting files and directories
 * 9. Visual tree display
 *
 * Design patterns in action:
 * ✓ COMPOSITE — File/Directory treated uniformly via FileSystemEntry
 * ✓ SINGLETON — FileSystem.getInstance() returns the same instance
 *
 * ══════════════════════════════════════════════════════════════════════
 */
public class FileSystemDemo {

    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════╗");
        System.out.println("║   IN-MEMORY FILE SYSTEM — LLD Design Demo    ║");
        System.out.println("╚═══════════════════════════════════════════════╝\n");

        // Reset for clean demo
        FileSystem.resetInstance();
        FileSystem fs = FileSystem.getInstance();

        // ──────────────────────────────────────────────────────────────
        // STEP 1: Create directories (mkdir -p creates intermediates)
        // ──────────────────────────────────────────────────────────────
        System.out.println("--- STEP 1: Create Directories ---");
        fs.mkdir("/home/user/documents");
        fs.mkdir("/home/user/downloads");
        fs.mkdir("/home/user/pictures/vacation");
        fs.mkdir("/home/admin");
        fs.mkdir("/etc/config");
        fs.mkdir("/tmp");

        // Show the tree so far
        fs.tree("/");

        // ──────────────────────────────────────────────────────────────
        // STEP 2: Create and write files
        // COMPOSITE PATTERN: Files (leaves) added to Directories (composites)
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- STEP 2: Create & Write Files ---");
        fs.touch("/home/user/.bashrc");
        fs.write("/home/user/.bashrc", "export PATH=$PATH:/usr/local/bin\nalias ll='ls -la'");

        fs.touch("/home/user/documents/resume.txt");
        fs.write("/home/user/documents/resume.txt",
                "John Doe\nSenior Software Engineer\n5+ years experience in Java, Python");

        fs.touch("/home/user/documents/notes.txt");
        fs.write("/home/user/documents/notes.txt", "LLD interview prep:\n- Design Patterns\n- SOLID principles");

        fs.touch("/etc/config/app.yaml");
        fs.write("/etc/config/app.yaml", "server:\n  port: 8080\n  host: localhost");

        fs.touch("/home/admin/readme.txt");
        fs.write("/home/admin/readme.txt", "Admin documentation");

        // Show the tree with files
        fs.tree("/");

        // ──────────────────────────────────────────────────────────────
        // STEP 3: Read file content
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- STEP 3: Read Files ---");
        String bashrc = fs.read("/home/user/.bashrc");
        String resume = fs.read("/home/user/documents/resume.txt");

        // ──────────────────────────────────────────────────────────────
        // STEP 4: List directory contents (ls)
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- STEP 4: List Directories (ls) ---");
        fs.ls("/");
        fs.ls("/home/user");
        fs.ls("/home/user/documents");
        // ls on a file returns just the file name
        fs.ls("/home/user/.bashrc");

        // ──────────────────────────────────────────────────────────────
        // STEP 5: Navigate with cd / pwd
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- STEP 5: Navigate (cd / pwd) ---");
        System.out.println("Current: " + fs.pwd()); // /

        fs.cd("/home/user/documents");
        System.out.println("After cd /home/user/documents: " + fs.pwd());

        // Relative path: go up with ..
        fs.cd("..");
        System.out.println("After cd ..: " + fs.pwd()); // /home/user

        // Relative path: go into subdirectory
        fs.cd("downloads");
        System.out.println("After cd downloads: " + fs.pwd()); // /home/user/downloads

        // Go back to root
        fs.cd("/");
        System.out.println("After cd /: " + fs.pwd()); // /

        // ──────────────────────────────────────────────────────────────
        // STEP 6: Path resolution with ".." and "."
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- STEP 6: Complex Path Resolution ---");
        // /home/user/../admin → /home/admin
        fs.cd("/home/user/../admin");
        System.out.println("cd /home/user/../admin → " + fs.pwd());

        // Read using complex path
        String adminReadme = fs.read("/home/user/../admin/./readme.txt");

        fs.cd("/");

        // ──────────────────────────────────────────────────────────────
        // STEP 7: Find files by name (recursive search)
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- STEP 7: Find Files ---");
        // Find all files named "readme.txt" starting from root
        fs.find("/", "readme.txt");

        // Find "documents" directory
        fs.find("/", "documents");

        // Find ".bashrc"
        fs.find("/home", ".bashrc");

        // ──────────────────────────────────────────────────────────────
        // STEP 8: Delete files and directories
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- STEP 8: Delete (rm) ---");
        fs.rm("/home/user/documents/notes.txt");
        fs.ls("/home/user/documents"); // only resume.txt left

        // Delete entire directory (recursive)
        fs.rm("/home/user/pictures");
        fs.ls("/home/user"); // pictures/ is gone

        // ──────────────────────────────────────────────────────────────
        // STEP 9: Final tree view
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- STEP 9: Final State ---");
        fs.tree("/");

        // ──────────────────────────────────────────────────────────────
        // STEP 10: Edge cases
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- STEP 10: Edge Cases ---");

        // Try to read non-existent file
        try {
            fs.read("/nonexistent.txt");
        } catch (IllegalArgumentException e) {
            System.out.println("Expected error: " + e.getMessage());
        }

        // Try to delete root
        try {
            fs.rm("/");
        } catch (IllegalArgumentException e) {
            System.out.println("Expected error: " + e.getMessage());
        }

        // Try to cd into a file
        try {
            fs.cd("/home/user/.bashrc");
        } catch (IllegalArgumentException e) {
            System.out.println("Expected error: " + e.getMessage());
        }

        // SINGLETON proof
        FileSystem fs2 = FileSystem.getInstance();
        System.out.println("\nSingleton check: same instance? " + (fs == fs2));

        System.out.println("\n╔═══════════════════════════════════════════════╗");
        System.out.println("║            Demo Complete!                    ║");
        System.out.println("║                                              ║");
        System.out.println("║  Patterns demonstrated:                      ║");
        System.out.println("║  ✓ Composite — File + Directory hierarchy    ║");
        System.out.println("║  ✓ Singleton — FileSystem.getInstance()     ║");
        System.out.println("╚═══════════════════════════════════════════════╝");
    }
}

package com.lld.patterns.composite;

/**
 * ══════════════════════════════════════════════════════════════════════
 * COMPOSITE PATTERN — Demo
 * ══════════════════════════════════════════════════════════════════════
 *
 * Demonstrates the Composite pattern using an Organization Hierarchy:
 *
 * Employee (Component)
 * ├── IndividualContributor (Leaf — no direct reports)
 * └── Manager (Composite — has direct reports, who are Employees)
 *
 * Key takeaways:
 * 1. Leaf and Composite share the SAME interface (Employee).
 * 2. Client code treats them UNIFORMLY — no instanceof checks.
 * 3. Operations aggregate RECURSIVELY through the tree.
 * 4. Adding new node types (e.g., Contractor) doesn't break clients.
 *
 * Real-world parallel: think of this as a mini File System —
 * Manager ≈ Directory (contains children)
 * IC ≈ File (leaf, no children)
 * The File System LLD we built earlier uses the SAME pattern!
 *
 * ══════════════════════════════════════════════════════════════════════
 */
public class CompositeDemo {

    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════╗");
        System.out.println("║      COMPOSITE PATTERN — Org Chart Demo      ║");
        System.out.println("╚═══════════════════════════════════════════════╝\n");

        // ──────────────────────────────────────────────────────────────
        // STEP 1: Create leaf nodes (Individual Contributors)
        // These are LEAVES in the Composite tree — no children.
        // ──────────────────────────────────────────────────────────────
        System.out.println("--- Building the org chart ---\n");

        IndividualContributor dev1 = new IndividualContributor("Alice", "Senior Developer", 120000);
        IndividualContributor dev2 = new IndividualContributor("Bob", "Developer", 100000);
        IndividualContributor dev3 = new IndividualContributor("Charlie", "Junior Developer", 80000);
        IndividualContributor qa1 = new IndividualContributor("Diana", "QA Engineer", 95000);
        IndividualContributor qa2 = new IndividualContributor("Eve", "QA Engineer", 90000);
        IndividualContributor designer = new IndividualContributor("Frank", "UI Designer", 100000);
        IndividualContributor accountant = new IndividualContributor("Grace", "Accountant", 85000);
        IndividualContributor hrRep = new IndividualContributor("Hank", "HR Specialist", 80000);

        // ──────────────────────────────────────────────────────────────
        // STEP 2: Create composite nodes (Managers) and build the tree
        // Managers contain Employees — which can be ICs or other Managers.
        // This recursive containment IS the Composite pattern.
        // ──────────────────────────────────────────────────────────────

        // Dev Lead manages developers
        Manager devLead = new Manager("Ivan", "Dev Lead", 150000);
        devLead.addReport(dev1); // adding Leaf to Composite
        devLead.addReport(dev2);
        devLead.addReport(dev3);

        // QA Lead manages QA engineers
        Manager qaLead = new Manager("Julia", "QA Lead", 130000);
        qaLead.addReport(qa1);
        qaLead.addReport(qa2);

        // CTO manages Dev Lead, QA Lead, and a designer
        // COMPOSITE PATTERN: CTO (Composite) contains devLead (Composite!) and qaLead
        // (Composite)
        // This is the RECURSIVE structure — composites containing other composites
        Manager cto = new Manager("Kevin", "CTO", 200000);
        cto.addReport(devLead); // adding Composite to Composite ← recursion!
        cto.addReport(qaLead); // adding Composite to Composite
        cto.addReport(designer); // adding Leaf to Composite

        // CFO manages accountant
        Manager cfo = new Manager("Laura", "CFO", 190000);
        cfo.addReport(accountant);

        // HR Director manages HR rep
        Manager hrDirector = new Manager("Mike", "HR Director", 140000);
        hrDirector.addReport(hrRep);

        // CEO is the root of the tree
        Manager ceo = new Manager("Nancy", "CEO", 300000);
        ceo.addReport(cto);
        ceo.addReport(cfo);
        ceo.addReport(hrDirector);

        // ──────────────────────────────────────────────────────────────
        // STEP 3: Show the hierarchy (recursive tree traversal)
        // ──────────────────────────────────────────────────────────────
        System.out.println("--- Full Organization Hierarchy ---\n");
        ceo.showHierarchy("");

        // ──────────────────────────────────────────────────────────────
        // STEP 4: Recursive operations — the power of Composite
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- Recursive Aggregation (Composite Pattern Power) ---\n");

        // UNIFORM INTERFACE: The same method works on ANY node
        // Whether it's a Leaf (IC) or a Composite (Manager), the client
        // just calls getHeadcount() and getTotalSalary().

        // On a Leaf — trivial
        System.out.printf("  Alice (IC):      headcount=%d, salary=$%.0f%n",
                dev1.getHeadcount(), dev1.getTotalSalary());

        // On a Composite — recursive aggregation
        System.out.printf("  Dev Lead team:   headcount=%d, salary=$%.0f%n",
                devLead.getHeadcount(), devLead.getTotalSalary());
        System.out.printf("  CTO org:         headcount=%d, salary=$%.0f%n",
                cto.getHeadcount(), cto.getTotalSalary());
        System.out.printf("  CEO (entire co): headcount=%d, salary=$%.0f%n",
                ceo.getHeadcount(), ceo.getTotalSalary());

        // ──────────────────────────────────────────────────────────────
        // STEP 5: Demonstrate uniform treatment
        // The client code below doesn't know (or care!) whether it's
        // working with an IC or a Manager — it uses the Employee interface.
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- Uniform Treatment (No instanceof needed!) ---\n");

        Employee[] anyEmployees = { dev1, devLead, cto, ceo, accountant };
        for (Employee e : anyEmployees) {
            // SAME code works for both Leaf and Composite
            System.out.printf("  %-20s | headcount: %2d | total salary: $%,.0f%n",
                    e.getName() + " (" + e.getTitle() + ")",
                    e.getHeadcount(),
                    e.getTotalSalary());
        }

        // ──────────────────────────────────────────────────────────────
        // STEP 6: Dynamic tree modification
        // ──────────────────────────────────────────────────────────────
        System.out.println("\n--- Dynamic Modification ---");
        System.out.printf("  Before: CTO headcount=%d, salary=$%.0f%n",
                cto.getHeadcount(), cto.getTotalSalary());

        // Add a new hire
        IndividualContributor newHire = new IndividualContributor("Oscar", "Intern", 50000);
        devLead.addReport(newHire);
        System.out.println("  Added Oscar (Intern) under Dev Lead");

        System.out.printf("  After:  CTO headcount=%d, salary=$%.0f%n",
                cto.getHeadcount(), cto.getTotalSalary());

        // Remove an employee
        devLead.removeReport(dev3);
        System.out.println("  Removed Charlie from Dev Lead's team");

        System.out.printf("  After:  CTO headcount=%d, salary=$%.0f%n",
                cto.getHeadcount(), cto.getTotalSalary());

        System.out.println("\n╔═══════════════════════════════════════════════╗");
        System.out.println("║            Demo Complete!                    ║");
        System.out.println("║                                              ║");
        System.out.println("║  Composite Pattern:                          ║");
        System.out.println("║  ✓ Component  — Employee (abstract base)     ║");
        System.out.println("║  ✓ Leaf       — IndividualContributor        ║");
        System.out.println("║  ✓ Composite  — Manager (contains Employees) ║");
        System.out.println("║  ✓ Recursive aggregation (salary, headcount) ║");
        System.out.println("║  ✓ Uniform treatment (no instanceof needed)  ║");
        System.out.println("╚═══════════════════════════════════════════════╝");
    }
}

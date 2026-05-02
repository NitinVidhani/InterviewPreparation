package com.lld.patterns.composite;

/**
 * ══════════════════════════════════════════════════════════════════════
 * DESIGN PATTERN: Composite — Leaf
 * ══════════════════════════════════════════════════════════════════════
 * IndividualContributor is a LEAF node in the Composite pattern.
 *
 * A leaf has NO children — it represents an end-point in the tree.
 * In our org chart: a developer, designer, or analyst with no direct
 * reports.
 *
 * Operations are trivial for leaves:
 * - getTotalSalary() → just this person's salary
 * - getHeadcount() → always 1
 *
 * The client doesn't need to know this is a leaf — it calls the same
 * methods as it would on a Manager (composite). That's the power of
 * the Composite pattern: uniform treatment.
 * ══════════════════════════════════════════════════════════════════════
 */
public class IndividualContributor extends Employee {

    public IndividualContributor(String name, String title, double salary) {
        super(name, title, salary);
    }

    @Override
    public double getTotalSalary() {
        // Leaf: just this employee's salary, no subordinates
        return salary;
    }

    @Override
    public int getHeadcount() {
        // Leaf: count is always 1 (just this person)
        return 1;
    }

    @Override
    public void showHierarchy(String indent) {
        System.out.println(indent + "👤 " + this);
    }
}

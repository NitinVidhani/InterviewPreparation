package com.lld.patterns.composite;

import java.util.ArrayList;
import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════════════
 * DESIGN PATTERN: Composite — Composite Node
 * ══════════════════════════════════════════════════════════════════════
 * Manager is the COMPOSITE node in the Composite pattern.
 *
 * A Manager is an Employee who ALSO has direct reports (children).
 * Those direct reports are of type Employee — meaning they can be
 * either IndividualContributors (leaves) or other Managers (sub-composites).
 *
 * This RECURSIVE STRUCTURE is what makes the Composite pattern powerful:
 *
 * CEO (Manager)
 * ├── CTO (Manager)
 * │ ├── Dev Lead (Manager)
 * │ │ ├── Dev 1 (IC - Leaf)
 * │ │ └── Dev 2 (IC - Leaf)
 * │ └── QA Lead (Manager)
 * │ └── QA 1 (IC - Leaf)
 * └── CFO (Manager)
 * └── Accountant (IC - Leaf)
 *
 * Operations aggregate recursively:
 * CEO.getTotalSalary() = CEO salary + CTO.getTotalSalary() +
 * CFO.getTotalSalary()
 * CTO.getTotalSalary() = CTO salary + DevLead.getTotalSalary() +
 * QALead.getTotalSalary()
 * DevLead.getTotalSalary() = DevLead salary + Dev1.salary + Dev2.salary
 * ...and so on recursively.
 *
 * The SAME method call (getTotalSalary()) works on ANY node in the tree,
 * whether it's a leaf or a composite. The client never needs to check
 * "is this a Manager or an IC?" — that's the Composite pattern's promise.
 * ══════════════════════════════════════════════════════════════════════
 */
public class Manager extends Employee {

    /**
     * List of direct reports — each is an Employee (Leaf or Composite).
     *
     * This is THE KEY FEATURE of the Composite pattern:
     * The container holds references to the COMPONENT type (Employee),
     * not to a specific concrete type. This enables the recursive
     * tree structure.
     */
    private final List<Employee> directReports;

    public Manager(String name, String title, double salary) {
        super(name, title, salary);
        this.directReports = new ArrayList<>();
    }

    /**
     * Add a direct report (IC or another Manager).
     *
     * COMPOSITE PATTERN: adding a Component to a Composite.
     * manager.addReport(new IndividualContributor(...)); // add leaf
     * manager.addReport(new Manager(...)); // add sub-composite
     *
     * Both work because the parameter type is Employee (the Component).
     */
    public void addReport(Employee employee) {
        directReports.add(employee);
    }

    /** Remove a direct report. */
    public void removeReport(Employee employee) {
        directReports.remove(employee);
    }

    /** Get the list of direct reports. */
    public List<Employee> getDirectReports() {
        return directReports;
    }

    /**
     * COMPOSITE PATTERN: Recursive aggregation.
     *
     * Total salary = this manager's salary + sum of ALL subordinates' total
     * salaries.
     *
     * Notice: we call getTotalSalary() on each report — which itself may
     * be a Manager (triggering deeper recursion) or an IC (base case).
     * The client doesn't know or care which — polymorphism handles it.
     */
    @Override
    public double getTotalSalary() {
        double total = this.salary; // start with this manager's salary
        for (Employee report : directReports) {
            total += report.getTotalSalary(); // recursive call — polymorphism!
        }
        return total;
    }

    /**
     * COMPOSITE PATTERN: Recursive headcount.
     *
     * Headcount = 1 (this manager) + sum of all subordinates' headcounts.
     */
    @Override
    public int getHeadcount() {
        int count = 1; // count this manager
        for (Employee report : directReports) {
            count += report.getHeadcount(); // recursive call
        }
        return count;
    }

    /**
     * Display the org hierarchy as an indented tree.
     *
     * This is another operation that PROPAGATES recursively through
     * the composite structure — each child prints itself and its
     * children, creating the tree visualization.
     */
    @Override
    public void showHierarchy(String indent) {
        System.out.println(indent + "👔 " + this + "  [" + directReports.size() + " direct reports]");
        for (int i = 0; i < directReports.size(); i++) {
            boolean isLast = (i == directReports.size() - 1);
            String connector = isLast ? "└── " : "├── ";
            String childIndent = indent + (isLast ? "    " : "│   ");
            directReports.get(i).showHierarchy(indent + connector);
        }
    }
}

package com.lld.patterns.composite;

/**
 * ══════════════════════════════════════════════════════════════════════
 * DESIGN PATTERN: Composite — Component (Abstract Base)
 * ══════════════════════════════════════════════════════════════════════
 * Employee is the COMPONENT in the Composite pattern.
 *
 * It defines the COMMON INTERFACE shared by both:
 * - IndividualContributor (Leaf — no direct reports)
 * - Manager (Composite — has direct reports who are also Employees)
 *
 * WHY an abstract class and not an interface?
 * We want shared state (name, title, salary) and default behavior
 * (toString, getters). An abstract class provides both.
 *
 * KEY INSIGHT: Every operation declared here works uniformly on both
 * leaves and composites. The client doesn't need to know whether it's
 * talking to an IC or a Manager — it just calls getSalary() or
 * getHeadcount().
 * ══════════════════════════════════════════════════════════════════════
 */
public abstract class Employee {

    protected final String name;
    protected final String title;
    protected double salary;

    protected Employee(String name, String title, double salary) {
        this.name = name;
        this.title = title;
        this.salary = salary;
    }

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title;
    }

    public double getSalary() {
        return salary;
    }

    /**
     * Get the total salary cost of this node AND all nodes below it.
     *
     * - Leaf: returns just this employee's salary.
     * - Composite: returns sum of this manager's salary + all reports' salaries.
     *
     * This recursive aggregation is the hallmark of the Composite pattern.
     */
    public abstract double getTotalSalary();

    /**
     * Get the total headcount at and below this node.
     *
     * - Leaf: returns 1.
     * - Composite: returns 1 + sum of all reports' headcounts.
     */
    public abstract int getHeadcount();

    /**
     * Print the org hierarchy from this node downward.
     *
     * @param indent the current indentation level for pretty-printing
     */
    public abstract void showHierarchy(String indent);

    @Override
    public String toString() {
        return String.format("%s (%s) - $%.0f", name, title, salary);
    }
}

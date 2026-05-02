package com.lld.patterns.abstractfactory;

/**
 * Driver class that demonstrates the Abstract Factory pattern.
 *
 * KEY TAKEAWAY:
 * The client code (buildUI method) works ENTIRELY with abstract interfaces
 * (UIFactory, Button, Checkbox). It has ZERO knowledge of the concrete
 * platform-specific classes. Switching the entire application from Windows
 * to macOS look-and-feel is a single-line change — just swap the factory.
 */
public class AbstractFactoryDemo {

    /**
     * Client code that uses the factory.
     * Notice: no platform-specific imports or references here.
     */
    private static void buildUI(UIFactory factory) {
        Button button = factory.createButton();
        Checkbox checkbox = factory.createCheckbox();

        button.render();
        button.onClick();
        checkbox.render();
        checkbox.toggle();
    }

    public static void main(String[] args) {
        System.out.println("=== Abstract Factory Pattern Demo ===\n");

        // Simulate detecting the OS at runtime
        String os = System.getProperty("os.name").toLowerCase();

        UIFactory factory;
        if (os.contains("mac")) {
            factory = new MacFactory();
        } else {
            factory = new WindowsFactory();
        }

        System.out.println("Detected OS: " + os);
        System.out.println("Using factory: " + factory.getClass().getSimpleName());
        System.out.println();

        // Build the UI — completely decoupled from the concrete platform
        buildUI(factory);

        // Show both factories for demo purposes
        System.out.println("\n--- Forcing Windows Factory ---");
        buildUI(new WindowsFactory());

        System.out.println("\n--- Forcing Mac Factory ---");
        buildUI(new MacFactory());
    }
}

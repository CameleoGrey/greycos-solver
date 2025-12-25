Automatic node sharing

When a ConstraintProvider does an operation for multiple constraints (such as finding all shifts corresponding to an employee), that work can be shared. This can significantly improve move evaluation speed if the repeated operation is computationally expensive:

nodeSharingValueProposition
2.3.1. Configuration
Plain Java

Spring Boot

Quarkus

Add <constraintStreamAutomaticNodeSharing>true</constraintStreamAutomaticNodeSharing> in your solverConfig.xml:

<!-- ... -->
<scoreDirectorFactory>
  <constraintProviderClass>org.acme.MyConstraintProvider</constraintProviderClass>
  <constraintStreamAutomaticNodeSharing>true</constraintStreamAutomaticNodeSharing>
</scoreDirectorFactory>
<!-- ... -->
xml
Copy
To use automatic node sharing outside Quarkus, your ConstraintProvider class must oblige by several restrictions so a valid subclass can be generated:

The ConstraintProvider class cannot be final.

The ConstraintProvider class cannot have any final methods.

The ConstraintProvider class cannot access any protected classes, methods or fields.

Debugging breakpoints put inside your constraints will not be respected, because the ConstraintProvider class will be transformed when this feature is enabled.

2.3.2. What is node sharing?
When using constraint streams, each building block forms a node in the score calculation network. When two building blocks are functionally equivalent, they can share the same node in the network. Sharing nodes allows the operation to be performed only once instead of multiple times, improving the performance of the solver. To be functionally equivalent, the following must be true:

The building blocks must represent the same operation.

The building blocks must have functionally equivalent parent building blocks.

The building blocks must have functionally equivalent inputs.

For example, the building blocks below are functionally equivalent:

Predicate<Shift> predicate = shift -> shift.getEmployee().getName().equals("Ann");

var a = factory.forEach(Shift.class)
.filter(predicate);

var b = factory.forEach(Shift.class)
.filter(predicate);
java
Copy
Whereas these building blocks are not functionally equivalent:

Predicate<Shift> predicate1 = shift -> shift.getEmployee().getName().equals("Ann");
Predicate<Shift> predicate2 = shift -> shift.getEmployee().getName().equals("Bob");

// Different parents
var a = factory.forEach(Shift.class)
.filter(predicate2);

var b = factory.forEach(Shift.class)
.filter(predicate1)
.filter(predicate2);

// Different operations
var a = factory.forEach(Shift.class)
.ifExists(Employee.class);

var b = factory.forEach(Shift.class)
.ifNotExists(Employee.class);

// Different inputs
var a = factory.forEach(Shift.class)
.filter(predicate1);

var b = factory.forEach(Shift.class)
.filter(predicate2);
java
Copy
Counterintuitively, the building blocks produced by these (seemly) identical methods are not necessarily functionally equivalent:

UniConstraintStream<Shift> a(ConstraintFactory constraintFactory) {
return factory.forEach(Shift.class)
.filter(shift -> shift.getEmployee().getName().equals("Ann"));
}

UniConstraintStream<Shift> b(ConstraintFactory constraintFactory) {
return factory.forEach(Shift.class)
.filter(shift -> shift.getEmployee().getName().equals("Ann"));
}
java
Copy
The Java Virtual Machine is free to (and often does) create different instances of functionally equivalent lambdas. This severely limits the effectiveness of node sharing, since the only way to know two lambdas are equal is to compare their references.

When automatic node sharing is used, the ConstraintProvider class is transformed so all lambdas are accessed via a static final field. Consider the following input class:

public class MyConstraintProvider implements ConstraintProvider {

    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
            a(constraintFactory),
            b(constraintFactory)
        };
    }

    Constraint a(ConstraintFactory constraintFactory) {
        return factory.forEach(Shift.class)
                      .filter(shift -> shift.getEmployee().getName().equals("Ann"))
                      .penalize(SimpleScore.ONE)
                      .asConstraint("a");
    }

    Constraint b(ConstraintFactory constraintFactory) {
        return factory.forEach(Shift.class)
                      .filter(shift -> shift.getEmployee().getName().equals("Ann"))
                      .penalize(SimpleScore.ONE)
                      .asConstraint("b");
    }
}
java
Copy
When automatic node sharing is enabled, the class will be transformed to look like this:

public class MyConstraintProvider implements ConstraintProvider {
private static final Predicate<Shift> $predicate1 = shift -> shift.getEmployee().getName().equals("Ann");

    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
            a(constraintFactory),
            b(constraintFactory)
        };
    }

    Constraint a(ConstraintFactory constraintFactory) {
        return factory.forEach(Shift.class)
                      .filter($predicate1)
                      .penalize(SimpleScore.ONE)
                      .asConstraint("a");
    }

    Constraint b(ConstraintFactory constraintFactory) {
        return factory.forEach(Shift.class)
                      .filter($predicate1)
                      .penalize(SimpleScore.ONE)
                      .asConstraint("b");
    }
}
java
Copy
This transformation means that debugging breakpoints placed inside the original ConstraintProvider will not be honored in the transformed ConstraintProvider.

From the above, you can see how this feature allows building blocks to share functionally equivalent parents, without needing the ConstraintProvider to be written in an awkward way.
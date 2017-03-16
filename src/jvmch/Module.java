package jvmch;

/**
 * This is an abstract class for a module of this project.
 *
 * @author Maxim Buzdalov
 */
public abstract class Module {
    public abstract boolean checkArgs(String[] args, int argumentOffset);
    public abstract boolean run(String[] args);
    public abstract String getUsage();
}

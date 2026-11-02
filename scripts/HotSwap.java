import com.sun.jdi.Bootstrap;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Replaces classes in a running game started with {@code --debug-jvm}, so a change in a method body can be seen in the live
 * world without a restart. Only method bodies can be replaced: a new method, field or class still needs a restart.
 *
 * <p>Usage, from the repository root:
 * <pre>
 *   java scripts/HotSwap.java 5005 resume                       # attach and let the waiting game run
 *   java scripts/HotSwap.java 5005 build/classes/java/main/.../Changed.class
 * </pre>
 *
 * <p>The class file must be compiled by the same Java version the game runs with, otherwise the virtual machine refuses it.
 */
public final class HotSwap {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java scripts/HotSwap.java <port> [resume] [class files...]");
            return;
        }
        int port = Integer.parseInt(args[0]);
        boolean resume = args.length > 1 && args[1].equals("resume");
        int firstClass = resume ? 2 : 1;

        AttachingConnector connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
                .filter(candidate -> candidate.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        Map<String, Connector.Argument> arguments = connector.defaultArguments();
        arguments.get("hostname").setValue("127.0.0.1");
        arguments.get("port").setValue(String.valueOf(port));
        arguments.get("timeout").setValue("30000");

        VirtualMachine vm = connector.attach(arguments);
        if (resume) {
            vm.resume();
            System.out.println("resumed");
        }
        if (args.length > firstClass) {
            Map<ReferenceType, byte[]> changes = new HashMap<>();
            for (int i = firstClass; i < args.length; i++) {
                Path file = Paths.get(args[i]);
                String name = file.toString().replace('\\', '/')
                        .replaceAll("^.*classes/(java|resources)/[^/]+/", "")
                        .replace('/', '.').replaceAll("\\.class$", "");
                ReferenceType type = vm.classesByName(name).stream().findFirst().orElse(null);
                if (type == null) {
                    System.out.println("not loaded, skipped: " + name);
                    continue;
                }
                changes.put(type, Files.readAllBytes(file));
                System.out.println("queued " + name);
            }
            if (!changes.isEmpty()) {
                vm.redefineClasses(changes);
                System.out.println("replaced " + changes.size() + " class(es)");
            }
        }
        vm.dispose();
    }
}

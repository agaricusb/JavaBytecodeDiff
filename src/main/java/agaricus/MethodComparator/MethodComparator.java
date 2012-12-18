package agaricus.MethodComparator;

import org.apache.commons.io.FilenameUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.Textifier;

import java.io.IOException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

//import org.objectweb.asm.*;

public class MethodComparator
{
    /**
     * Read classes in a jar file into ASM ClassNodes
     * @param jarFilename Name of jar to read
     * @param packagePrefix Prefix to match for classes to read (all others ignored)
     * @return Map of class name String to ClassNode
     */
    public static LinkedHashMap<String,LinkedHashMap<String, MethodNode>> getClasses(String jarFilename, String packagePrefix) throws IOException
    {
        JarFile jarFile = new JarFile(jarFilename);
        Enumeration<JarEntry> entries = jarFile.entries();

        LinkedHashMap<String,LinkedHashMap<String, MethodNode>> classes = new LinkedHashMap<String, LinkedHashMap<String, MethodNode>>();

        while(entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String path = entry.getName();

            if (!path.startsWith(packagePrefix) || !path.endsWith(".class")) {
                continue;
            }

            String className = FilenameUtils.getBaseName(path);

            ClassReader classReader = new ClassReader(jarFile.getInputStream(entry));
            ClassNode classNode = new ClassNode();

            classReader.accept(classNode, 0);

            // Ordered list of methods
            LinkedHashMap<String, MethodNode> methods = new LinkedHashMap<String, MethodNode>();

            for (MethodNode methodNode: (List<MethodNode>)classNode.methods) {
                // Key on the method name, space, and the descriptor (Java type string, like: ()Z)
                // Not using methodNode.signature since it seems to always be null
                String key = methodNode.name + " " + methodNode.desc;

                // Remove package for comparison purposes, otherwise will detect
                // net/minecraft/server -> net/minecraft/server/v1_4_5 differences in all methods
                key = key.replace(packagePrefix, "");

                methods.put(key, methodNode);

                // TODO: store fields, so can detect private -> public access modifiers, and type changes
            }

            classes.put(className, methods);
        }

        return classes;
    }

    /**
     * Compute asymmetric set difference
     * @param s1 minuend
     * @param s2 subtrahend
     * @param <T>
     * @return s1 - s2
     */
    public static <T> Set<T> setDifference(Set<T> s1, Set<T> s2)
    {
        Set<T> diff = new HashSet<T>(s1);
        diff.removeAll(s2);
        return diff;
    }

    /**
     * Compute set intersection
     * @param s1
     * @param s2
     * @param <T>
     * @return s1 & s2
     */
    public static <T> Set<T> setIntersection(Set<T> s1, Set<T> s2) {
        Set<T> intersection = new HashSet<T>(s1);
        intersection.retainAll(s2);
        return intersection;
    }

    /**
     * Compare classes in cs1 with classes in cs2
     * @param cs1 Set of classes to compare
     * @param cs2 Set of classes to compare with (extra classes are ignored)
     */
    public static void compareClasses(Map<String, LinkedHashMap<String, MethodNode>> cs1, Map<String, LinkedHashMap<String, MethodNode>> cs2)
    {
        for (Map.Entry<String, LinkedHashMap<String, MethodNode>> entry1 : cs1.entrySet()) {
            String className = entry1.getKey();
            LinkedHashMap<String, MethodNode> methods1 = entry1.getValue();
            LinkedHashMap<String, MethodNode> methods2 = cs2.get(className);

            Set<String> removed = setDifference(methods1.keySet(), methods2.keySet());
            Set<String> added = setDifference(methods2.keySet(), methods1.keySet());

            // Added/removed methods
            if (!removed.isEmpty() || !added.isEmpty()) {
                for (String remove: removed) {
                    String[] a = remove.split(" ");
                    String removeName = a[0];
                    String removeSignature = a[1];
                    boolean changedSignature = false;
                    for (String add: added) {
                        String[] b = add.split(" ");
                        String addName = b[0];
                        String addSignature = b[1];
                        if (removeName.equals(addName)) {
                            // Changed method signature - but same name
                            System.out.println("MD:SIG: " + className + " " + removeName + " " + removeSignature + " " + addName + " " + addSignature);
                            changedSignature = true;
                        }
                    }
                    if (!changedSignature) {
                        System.out.println("MD:REM: " + className + " " + remove);
                    }
                }
                for (String add: added) {
                    System.out.println("MD:ADD: " + className + " " + add);
                }
            }
            // TODO: detect access changes - .access bit field (e.g. private -> public)

            for (String methodName: setIntersection(methods1.keySet(), methods2.keySet())) {
                MethodNode m1 = methods1.get(methodName);
                MethodNode m2 = methods2.get(methodName);

                // TODO: detect changed methods
                //TODO compareMethods(className, m1, m2);
            }
        }
    }

    public static void compareMethods(String className, MethodNode m1, MethodNode m2) {
        ASMifier a1 = new ASMifier();
        // TODO a1.visit();



        AbstractInsnNode inst1 = m1.instructions.getFirst();
        AbstractInsnNode inst2 = m2.instructions.getFirst();

        System.out.println("MD:???: " + className + " " + m1.name + " = " + inst1.equals(inst2) + " . " + inst1 + " ? " + inst2);
    }

    public static boolean compareInstructions(AbstractInsnNode inst1, AbstractInsnNode inst2) {
        /*
        switch(inst1.getType()) {

        }
        */


        return false;
    }

    public static void main(String[] args) throws IOException
    {
        String filename1 = "/tmp/minecraft-server-1.4.5.jar";
        String filename2 = "/tmp/craftbukkit-1.4.5-R0.3-2536.jar";

        String prefix1 = "net/minecraft/server/";
        String prefix2 = "net/minecraft/server/v1_4_5/";

        LinkedHashMap<String,LinkedHashMap<String,MethodNode>> cs1 = getClasses(filename1, prefix1);
        LinkedHashMap<String,LinkedHashMap<String,MethodNode>> cs2 = getClasses(filename2, prefix2);

        System.out.println(cs1.size() + " / " + cs2.size());

        Set<String> missing = setDifference(cs1.keySet(), cs2.keySet());
        for (String m: missing) {
            System.out.println("CL:REM: " + m);
        }

        if (!missing.isEmpty()) {
            System.out.println("Missing classes! " + filename1 + " - " + filename2 + " = " + missing);
            return;
        }

        Set<String> surplus = setDifference(cs2.keySet(), cs1.keySet());
        for (String s: surplus) {
            System.out.println("CL:ADD: " + s);
        }

        compareClasses(cs1, cs2);

    }
}

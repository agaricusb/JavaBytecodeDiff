package agaricus.JavaBytecodeDiff;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;

import javax.sql.rowset.Joinable;
import javax.swing.plaf.basic.BasicInternalFrameTitlePane;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

//import org.objectweb.asm.*;

public class JavaBytecodeDiff
{
    /**
     * Read classes in a jar file into ASM ClassNodes
     * @param jarFilename Name of jar to read
     * @param packagePrefix Prefix to match for classes to read (all others ignored)
     * @return Map of class name String to ClassNode
     */
    @SuppressWarnings("unchecked")
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

            //classReader.accept(classNode, ClassReader.EXPAND_FRAMES);
            //classReader.accept(classNode, ClassReader.SKIP_FRAMES);
            classReader.accept(classNode, 0);

            // Ordered list of methods
            LinkedHashMap<String, MethodNode> methods = new LinkedHashMap<String, MethodNode>();

            for (MethodNode methodNode: (List<MethodNode>)classNode.methods /* unchecked :( */) {
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
                            // TODO: add to methods2 so can compare methods further, even if signature changes
                        }
                    }
                    if (!changedSignature) {
                        System.out.println("MD:Rem: " + className + " " + remove);
                    }
                }
                for (String add: added) {
                    System.out.println("MD:Add: " + className + " " + add);
                }
            }

            for (String methodName: setIntersection(methods1.keySet(), methods2.keySet())) {
                MethodNode m1 = methods1.get(methodName);
                MethodNode m2 = methods2.get(methodName);

                compareMethodAccess(className, m1, m2);

                compareMethods(className, m1, m2);
            }

            // TODO: compare fields, for type and access changes
        }
    }

    // Access flags to ignore for comparison purposes
    // Ignore synthetic, because the modding tools widely used by the Minecraft community are not
    // sophisticated enough to preserve this flag, in most cases - during decompilation,
    // it is ignored. (This method was generated by the compiler for type erasure.)
    // see http://aruld.info/synthetic-methods-in-java/
    final static int ACCESS_IGNORE_MASK = Opcodes.ACC_SYNTHETIC;

    /**
     * Compare access specifiers
     */
    public static void compareMethodAccess(String className, MethodNode m1, MethodNode m2) {
        // TODO: refactor for fields, and compare those, too


        if ((m1.access & ~ACCESS_IGNORE_MASK) != (m2.access & ~ACCESS_IGNORE_MASK)) {
            String access1 = accessToString(m1.access);
            String access2 = accessToString(m2.access);
            // Method access change (e.g. private -> public)
            System.out.println("MD:Acc: " + className + " " + m1.name + " " + m1.desc + " " + access1 + " " + m2.name + " " + m2.desc + " " + access2);
        }
    }

    public static String accessToString(int access) {
        List<String> accessStrings = new ArrayList<String>();
        // based on org.objectweb.asm.utils.Textifier appendAccess()
        if ((access & Opcodes.ACC_PUBLIC) != 0) {
            accessStrings.add("public");
        }
        if ((access & Opcodes.ACC_PRIVATE) != 0) {
            accessStrings.add("private");
        }
        if ((access & Opcodes.ACC_PROTECTED) != 0) {
            accessStrings.add("protected");
        }
        if ((access & Opcodes.ACC_FINAL) != 0) {
            accessStrings.add("final");
        }
        if ((access & Opcodes.ACC_STATIC) != 0) {
            accessStrings.add("static");
        }
        if ((access & Opcodes.ACC_SYNCHRONIZED) != 0) {
            accessStrings.add("synchronized");
        }
        if ((access & Opcodes.ACC_VOLATILE) != 0) {
            accessStrings.add("volatile");
        }
        if ((access & Opcodes.ACC_TRANSIENT) != 0) {
            accessStrings.add("transient");
        }
        if ((access & Opcodes.ACC_ABSTRACT) != 0) {
            accessStrings.add("abstract");
        }
        if ((access & Opcodes.ACC_STRICT) != 0) {
            accessStrings.add("strictfp");
        }
        if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
            accessStrings.add("synthetic");
        }
        if ((access & Opcodes.ACC_ENUM) != 0) {
            accessStrings.add("enum");
        }
        if (accessStrings.size() == 0) {
            accessStrings.add("-");
        }

        return StringUtils.join(accessStrings, ',');
    }

    public static void compareMethods(String className, MethodNode m1, MethodNode m2) {
        //ASMifier a1 = new ASMifier();
        // TODO a1.visit();

        boolean differ = false;

        AbstractInsnNode insn1 = m1.instructions.getFirst();
        AbstractInsnNode insn2 = m2.instructions.getFirst();

        // TODO: perform full assembly

        while(insn1 != null && insn2 != null) {
            // Skip frames
            while(insn1 != null && insn1.getOpcode() == -1) {
                insn1 = insn1.getNext();
            }
            while(insn2 != null && insn2.getOpcode() == -1) {
                insn2 = insn2.getNext();
            }

            String o1 = "", o2 = "";
            if (insn1 != null) {
                o1 = opcodeToString(insn1.getOpcode());
            }
            if (insn2 != null) {
                o2 = opcodeToString(insn2.getOpcode());
            }

            // Compare
            if (!o1.equals(o2)) { // TODO: compare operands
                differ = true;
            }
            //System.out.println(" " + o1 + "\t" + o2 + "\t" + (differ ? " <--" : ""));

            if (insn1 != null) {
                insn1 = insn1.getNext();
            }

            if (insn2 != null) {
                insn2 = insn2.getNext();
            }
        }

        // Skip frames
        while(insn1 != null && insn1.getOpcode() == -1) {
            insn1 = insn1.getNext();
        }
        while(insn2 != null && insn2.getOpcode() == -1) {
            insn2 = insn2.getNext();
        }


        while(insn1 != null) {
            //System.out.println(" " + opcodeToString(insn1.getOpcode()) + "\t---\t");
            insn1 = insn1.getNext();
            differ = true;
        }
        while(insn2 != null) {
            //System.out.println(" ---\t" + opcodeToString(insn2.getOpcode()) + "\t");
            insn2 = insn2.getNext();
            differ = true;
        }

        if (differ)  {
            System.out.println("MD:Edt: " + className + " " + m1.name + " " + m1.desc + " " + m2.name + " " + m2.desc);
        } else {
            //System.out.println("MD:Sam: " + className + " " + m1.name + " " + m1.desc + " " + m2.name + " " + m2.desc);
        }
    }

    public static String opcodeToString(int op) {
        if (op == -1) {
            return "";
        }
        return Printer.OPCODES[op];
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
        if (args.length != 4) {
            System.out.println("usage: java -jar JavaBytecodeDiff.jar jar1 jar2 prefix1 prefix2");
            System.out.println("Example:\n");
            System.out.println("\tjava -jar JavaBytecodeDiff.jar ../jars/minecraft-server-1.4.6.jar ../jars/craftbukkit-1.4.6-R0.3.jar net/minecraft/server net/minecraft/server/v1_4_6");
            System.exit(-1);
        }

        // TODO: flags
        String filename1 = args[0];
        String filename2 = args[1];

        String prefix1 = args[2];
        String prefix2 = args[3];

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

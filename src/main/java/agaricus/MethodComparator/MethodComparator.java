package agaricus.MethodComparator;

import org.apache.commons.io.FilenameUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

//import org.objectweb.asm.*;

public class MethodComparator
{
    /**
     * Read classes in a jar file into ASM ClassNodes
     * @param jarFilename Name of jar to read
     * @param packagePrefix Prefix to match for classes to read (all others ignored)
     * @return Map of class name String to ClassNode
     */
    public static Map<String,ClassNode> getClassNodes(String jarFilename, String packagePrefix) throws IOException
    {
        JarFile jarFile = new JarFile(jarFilename);
        Enumeration<JarEntry> entries = jarFile.entries();

        Map<String,ClassNode> classes = new HashMap<String,ClassNode>();

        while(entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String path = entry.getName();

            if (!path.startsWith(packagePrefix) || !path.endsWith(".class")) {
                continue;
            }

            String className = FilenameUtils.getBaseName(path);
            System.out.println(className);

            ClassReader classReader = new ClassReader(jarFile.getInputStream(entry));
            ClassNode classNode = new ClassNode();

            classReader.accept(classNode, 0);

            classes.put(className, classNode);
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

    public static void main(String args[]) throws IOException
    {
        String filename1 = "/tmp/minecraft-server-1.4.5.jar";
        String filename2 = "/tmp/craftbukkit-1.4.5-R0.3-2536.jar";

        Map<String,ClassNode> cs1 = getClassNodes(filename1, "net/minecraft/server/");
        Map<String,ClassNode> cs2 = getClassNodes(filename2, "net/minecraft/server/v1_4_5/");

        System.out.println(cs1.size() + " / " + cs2.size());

        Set<String> missing = setDifference(cs1.keySet(), cs2.keySet());
        if (!missing.isEmpty()) {
            System.out.println("Missing classes! " + filename1 + " - " + filename2 + " = " + missing);
            return;
        }

        Set<String> surplus = setDifference(cs2.keySet(), cs1.keySet());
        System.out.println("Classes added (" + surplus.size() + "): " + surplus);


        /*
        for (MethodNode methodNode: (List<MethodNode>)c1.methods) {
            System.out.println("\t "+methodNode.name+" "+methodNode.desc);
        }
        */
    }
}

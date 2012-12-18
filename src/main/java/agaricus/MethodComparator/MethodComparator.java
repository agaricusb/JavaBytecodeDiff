package agaricus.MethodComparator;

import org.apache.commons.io.FilenameUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

//import org.objectweb.asm.*;

public class MethodComparator
{
    public static void main(String args[]) throws IOException
    {
        JarFile jarFile = new JarFile("/tmp/minecraft-server-1.4.5.jar");
        Enumeration<JarEntry> entries = jarFile.entries();

        Map<String,String> classes = new HashMap<String,String>();

        while(entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            System.out.println("jarEntry = "+entry);

            String path = entry.getName();

            if (path.startsWith("net/minecraft/server/")) {
                String className = FilenameUtils.getBaseName(path);

                ClassReader classReader = new ClassReader(jarFile.getInputStream(entry));
                ClassNode classNode = new ClassNode();

                classReader.accept(classNode, 0);

                System.out.println(className);
                //classData.put(className, jarFile.getInputStream(entry).);

                for (MethodNode methodNode: (List<MethodNode>)classNode.methods) {
                    System.out.println("\t "+methodNode.name+" "+methodNode.desc);
                }
            }
        }
    }
}

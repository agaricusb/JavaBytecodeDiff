Compare Java bytecode

Example usage:

    java -jar target/JavaBytecodeDiff-1.0.jar ../jars/minecraft-server-1.4.6.jar ../jars/craftbukkit-1.4.6-R0.3.jar net/minecraft/server net/minecraft/server/v1_4_6

where minecraft-server-1.4.6.jar is the "internally-renamed" jar from http://repo.bukkit.org/content/groups/public/org/bukkit/minecraft-server/ , and the CraftBukkit build is from http://dl.bukkit.org/.

Shows classes added, and for each class: methods added, removed, signature changed, and edited. Full output: https://gist.github.com/4449702

The method-edited detection is not too reliable yet since decompile/recompile significantly alters the bytecode.


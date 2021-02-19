/*
 * Copyright 2021 by Carl Grundstrom. All rights reserved
 *
 * License: Apache License V2.0. See https://www.apache.org/licenses/LICENSE-2.0
 */
package org.javasearchclasspath;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.io.IOException;

import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.LinkedList;
import java.util.jar.JarInputStream;
import java.util.jar.JarEntry;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.text.SimpleDateFormat;

/**
 * SearchClassPath has two modes of operation. The first simply displays the
 * classpath. The second searches the classpath for a specified class.
 * <p>
 * The classpath to be displayed or searched is passed to the program via a
 * java property, "searchClassPath".
 * <p>
 * The class to search for is passed via a command line argument and is
 * optional. If desired, the "-p" option can be used so that you can search
 * on a partial class name. This take somewhat longer as it forces the program
 * to scan directories in the class path.
 */
public class JavaSearchClassPath {
    // Flag indicating if we found a match
    private static boolean found = false;

    // Command line options
    private static boolean cShowHelp = false;
    private static boolean cPartialMatch = false;
    private static boolean cCheckDuplicateJars = false;
    private static String cSep = System.getProperty("path.separator");
    private static String cBootClassPathProperty = "sun.boot.class.path";
    private static boolean cQuiet = false;
    private static String cExtension = ".class";

    // The classpath to display or search
    private static String cSearchClassPath = System.getProperty("searchClassPath");

    // The file name of the class without a ".class" extension
    private static String cClassNameNoExt;

    // The file name of the class with a ".class" extension
    private static String cClassName;

    // The list of class directories and jar files to display or search
    private static LinkedList<String> cClassPathList;

    // The formating object used to display dates
    private static SimpleDateFormat cFormat;

    // We can optionally search for a package name instead of a class
    private static boolean cSearchForPackage;

    /**
     * The package name to search for
     */
    private static String cPackageName;
    private static String cPackageNameJar;
    private static String cPackageNameDir;

    /**
     * the list of jars found so jar, used by cCheckDuplicateJars
     */
    private static HashMap<String, List<File>> cDuplicateJars;

    /**
     * The main routine, parses command line options, builds the list of
     * class directories and jar files, and displays or searches the classpath
     *
     * @param args command line arguements
     */
    public static void main(String[] args) {
        try {
            //
            // Make sure the user specified the classpath to display or search
            if (cSearchClassPath == null) {
                System.err.println("you must use \"java -DsearchClassPath=$CLASSPATH\"");
                System.exit(1);
            }

            //
            // Parse the command line options
            //
            Options options = new Options(args, "b:de:hpPs:q");
            while (options.hasNext()) {
                switch (options.next()) {
                    // 'b' allows the user to specify the boot class path property
                    case 'b':
                        cBootClassPathProperty = options.getValue();
                        break;

                    // 'd' will warn the users if a jar file appears more than once in the classpath
                    case 'd':
                        cCheckDuplicateJars = true;
                        cDuplicateJars = new HashMap<>();
                        break;

                    // 'e' allows the user to specify an extension other than ".class"
                    case 'e':
                        cExtension = options.getValue();
                        if (!cExtension.startsWith(".")) {
                            cExtension = "." + cExtension;
                        }
                        break;

                    // 'h' displays the usage statement
                    case 'h':
                        cShowHelp = true;
                        break;

                    // 'p' allows the user to specify only part of the class name
                    case 'p':
                        cPartialMatch = true;
                        break;

                    // 'P' allows the user to search for a package name instead of a class
                    case 'P':
                        cSearchForPackage = true;
                        break;

                    // 's' allows the user to specify the class path separator
                    case 's':
                        cSep = options.getValue();
                        break;

                    // 'q' tells us not to print warnings
                    case 'q':
                        cQuiet = true;
                        break;
                }
            }

            //
            // Make sure we have the correct number of arguments (0 or 1)
            //
            int idx = options.getIndex();
            if (cShowHelp || idx != args.length && idx + 1 != args.length) {
                usage();
            }

            System.out.println();
            if (cSearchForPackage) {
                if (idx + 1 != args.length) {
                    System.err.println("you must specify a package name if you use the '-P' option");
                    System.exit(1);
                }
                cPackageName = args[idx++];
                cPackageNameJar = cPackageName + "/";

                char fileSeparator = System.getProperty("file.separator").charAt(0);
                cPackageNameDir = cPackageName.replace('.', fileSeparator);
                if (fileSeparator == '/') {
                    cPackageNameDir = cPackageNameDir.replace('\\', fileSeparator);
                }
                else {
                    cPackageNameDir = cPackageNameDir.replace('/', fileSeparator);
                    cPackageNameJar = cPackageNameDir.replace(fileSeparator, '/');
                }
                if (!cPackageNameJar.endsWith("/")) {
                    cPackageNameJar = cPackageNameJar + "/";
                }

                if (cPartialMatch) {
                    System.out.println("Searching for package '*" + cPackageName + "*'");
                }
                else {
                    System.out.println("Searching for package '" + cPackageName + "'");
                }
            }
            else {
                //
                // Optionally get the class name from the command line
                //
                String userProvidedClassName = null;
                if (idx + 1 == args.length) {
                    userProvidedClassName = args[idx++];
                }

                //
                // If we have a class to search for, build the file names
                // in advance
                //
                if (userProvidedClassName != null) {
                    userProvidedClassName = userProvidedClassName.replace('\\', '/');

                    int idx1 = userProvidedClassName.lastIndexOf('/');
                    if (idx1 != -1) {
                        int idx2 = userProvidedClassName.lastIndexOf('.', idx1);
                        if (idx2 != -1) {
                            cExtension = userProvidedClassName.substring(idx2);
                            userProvidedClassName = userProvidedClassName.substring(0, idx2);
                        }
                    }
                    if (userProvidedClassName.endsWith(cExtension)) {
                        userProvidedClassName = userProvidedClassName.substring(0, userProvidedClassName.length() - cExtension.length());
                    }
                    userProvidedClassName = userProvidedClassName.replace('.', '/');

                    cClassNameNoExt = userProvidedClassName;
                    cClassName = cClassNameNoExt + cExtension;
                    if (cPartialMatch) {
                        System.out.println("Searching for '*" + cClassNameNoExt + "*" + cExtension + "'");
                    }
                    else {
                        System.out.println("Searching for '" + cClassName + "'");
                    }
                }
                cFormat = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
            }

            //
            // Build the list of class directories and jar files
            //
            buildClasspathList();

            //
            // Scan the list we just built
            //
            scanClassPathList();

            if (cSearchForPackage) {
                //
                // Print an error if the package was not found
                //
                if (!found) {
                    System.err.println("Package '" + cPackageName + "' not found in path");
                }
            }
            else {
                //
                // Print an error if the class was specified but not found
                //
                if (cClassName != null && !found) {
                    System.err.println(cClassName + "' not found in path");
                }
            }

            if (cCheckDuplicateJars) {
                for (Map.Entry<String, List<File>> entry : cDuplicateJars.entrySet()) {
                    String name = entry.getKey();
                    List<File> value = entry.getValue();
                    int len = value.size();
                    if (len > 1) {
                        System.err.println();
                        System.err.println("Duplicate classpath entry: " + name);
                        for (File f : value)
                            System.err.println("    " + f);
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Print a usage statement and exit with an error status
     */
    private static void usage() {
        PrintStream out = cShowHelp ? System.out : System.err;

        out.println();
        out.println("SearchClassPath has two modes of operation. The first simply displays the");
        out.println("classpath. The second searches the classpath for a specified class.");
        out.println("If you specify a class name, searchClassPath will search for it. Otherwise it");
        out.println("will simply display the classpath.");
        out.println();
        out.println("The classpath to be displayed or searched is passed to the program via a");
        out.println("java property, \"searchClassPath\". Use the -D option to \"java\" to set this");
        out.println("property.");
        out.println();
        out.println("The class to search for is passed via a command line argument and is");
        out.println("optional. If desired, the \"-p\" option can be used so that you can search");
        out.println("on a partial class name. This takes somewhat longer as it requires the program");
        out.println("to scan all directories in the class path. Note that the partial class name search");
        out.println("will check for matches in the package name as well as the class name");
        out.println();
        out.println("usage: java -DsearchClassPath=$CLASSPATH tools.path.SearchClassPath [-hp]");
        out.println("          [-b <bootclasspath>] [-e <extension>] [-s <separator>] <path> [<class>]");
        out.println();
        out.println("  -b   allows you to set the property used to find the boot class path. This");
        out.println("       defaults to sun.boot.class.path (supports the Sun JVM).");
        out.println("  -d   warns you if a jar file appears more than once in the classpath");
        out.println("  -e   allows you to search for a file other than a java class file. Specify");
        out.println("       the extension of the file you are searching for. For example: -e properties");
        out.println("  -h   shows this help message");
        out.println("  -p   allows you to specific a partial class name");
        out.println("  -P   search for a package name instead of a class");
        out.println("  -s   specifies the separator used in the classpath (default is semi-colin");
        out.println("       on NT and colin on UNIX)");
        out.println("  -q   don't display warnings");
        out.println();

        System.exit(cShowHelp ? 0 : 1);
    }

    /**
     * Builds a list of class directories and jar files
     */
    private static void buildClasspathList() {
        cClassPathList = new LinkedList<>();

        //
        // Add bootstrap path. We only add items that exist, since
        // $JAVA_HOME/jre/classes is normally not created.
        //
        // Note that this code is specific to the Sun JVM but could potentially
        // be adapted to other JVMs via the "-b" option.
        //
        StringTokenizer tok;
        String bootpath = System.getProperty(cBootClassPathProperty);
        if (bootpath != null) {
            tok = new StringTokenizer(bootpath, System.getProperty("path.separator"));
            while (tok.hasMoreTokens()) {
                File f = new File(tok.nextToken());
                if (f.exists()) {
                    cClassPathList.add(f.toString());
                }
            }
        }

        //
        // Add the lib/ext directory
        //
        String javaHome = System.getProperty("java.home");
        File extDir = new File(javaHome, "lib/ext");
        File[] files = extDir.listFiles();
        if (files != null) {
            for (File f : files) {
                cClassPathList.add(f.toString());
            }
        }

        //
        // Add the specified classpath (not ours, but the one passed to us
        // via the java options)
        //  
        tok = new StringTokenizer(cSearchClassPath, cSep);
        while (tok.hasMoreTokens()) {
            cClassPathList.add(tok.nextToken());
        }
    }

    /**
     * Scan the classpath list for the class file
     */
    private static void scanClassPathList()
            throws Exception {
        for (String s : cClassPathList) {
            File f = new File(s);
            if (f.exists()) {
                if (cClassName == null && !cSearchForPackage) {
                    System.out.println(s);
                }
                else {
                    if (f.isDirectory()) {
                        searchDirectory(f);
                    }
                    else {
                        searchJar(f);
                    }
                }

                if (cCheckDuplicateJars && !f.isDirectory()) {
                    String name = f.getName();
                    List<File> list = cDuplicateJars.computeIfAbsent(name, k -> new ArrayList<>());
                    list.add(f);
                }
            }
            else {
                if (cClassName == null) {
                    if (!cQuiet) {
                        System.out.println(s);
                        System.err.println("warning: " + s + " does not exist");
                    }
                    else {
                        System.out.println(s);
                    }
                }
                else {
                    if (!cQuiet) {
                        System.err.println("warning: " + s + " does not exist");
                    }
                }
            }
        }
    }

    /**
     * Check a class directory for the class file
     *
     * @param dir the directory to search
     */
    private static void searchDirectory(File dir) {
        if (cSearchForPackage) {
            if (cPartialMatch) {
                scanDir(dir, dir);
            }
            else {
                File d = new File(dir, cPackageNameDir);
                if (d.exists() && d.isDirectory()) {
                    foundPackage(dir, cPackageNameDir);
                }
            }
        }
        else {
            if (cPartialMatch) {
                scanDir(dir, dir);
            }
            else {
                File f = new File(dir, cClassName);
                if (f.exists()) {
                    foundClass(dir, cClassName, f.lastModified(), f.length());
                }
            }
        }
    }

    /**
     * Searches a directory recursively, looking for the class. Used only for
     * partial name searches
     */
    private static void scanDir(File top, File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    if (cSearchForPackage) {
                        String path = f.getPath();
                        if (path.contains(cPackageNameDir)) {
                            String s = f.getAbsolutePath().substring(top.getAbsolutePath().length());
                            foundPackage(top, s);
                        }
                    }
                    scanDir(top, f);
                }
                else {
                    if (!cSearchForPackage) {
                        if (f.getName().endsWith(cExtension)) {
                            String path = f.getPath().replace('\\', '/');
                            if (path.contains(cClassNameNoExt) && path.endsWith(cExtension)) {
                                String s = f.getAbsolutePath().substring(top.getAbsolutePath().length());
                                if (s.charAt(0) == '\\' || s.charAt(0) == '/') {
                                    s = s.substring(1);
                                }
                                foundClass(top, s, f.lastModified(), f.length());
                            }
                        }
                    }
                }
            }
        }

    }

    /**
     * Searches a jar file for the specified class
     */
    private static void searchJar(File jar)
            throws Exception {
        Set<String> foundPackages = null;
        if (cSearchForPackage) {
            foundPackages = new HashSet<>();
        }

        JarInputStream in;
        try {
            in = new JarInputStream(new FileInputStream(jar));
        }
        catch (Exception e) {
            System.err.println("warning: cannot open " + jar + " as a jar file: " + e.toString());
            return;
        }
        while (true) {
            JarEntry entry = in.getNextJarEntry();
            if (entry == null) {
                break;
            }
            String entryName = entry.getName();
            if (cSearchForPackage) {
                String packageName = entryName;
                if (packageName.endsWith(".class")) {
                    int idx = packageName.lastIndexOf('/');
                    if (idx == -1)
                        continue;
                    packageName = packageName.substring(0, idx + 1);
                }
                if (cPartialMatch) {
                    if (packageName.contains(cPackageNameJar) && !foundPackages.contains(packageName)) {
                        foundPackage(jar, packageName);
                        foundPackages.add(packageName);
                    }
                }
                else {
                    if (packageName.equals(cPackageNameJar) && !foundPackages.contains(packageName)) {
                        foundPackage(jar, packageName);
                        foundPackages.add(packageName);
                    }
                }
            }
            else {
                if (cPartialMatch) {
                    if (entryName.contains(cClassNameNoExt) && entryName.endsWith(cExtension)) {
                        foundClass(jar, entryName, entry.getTime(), getEntrySize(in));
                    }
                }
                else {
                    if (entryName.equals(cClassName)) {
                        foundClass(jar, entryName, entry.getTime(), getEntrySize(in));
                    }
                }
            }
        }
    }

    /**
     * Returns the size of the current entry in the Jar file. getSize() always
     * seems to return -1, so we read in the entry to figure out it's size
     */
    private static int getEntrySize(JarInputStream in)
            throws IOException {
        int size = 0;
        int ret;
        byte[] buf = new byte[1024];
        while ((ret = in.read(buf)) > 0)
            size += ret;
        return size;
    }

    /**
     * Prints out information on a location where we've found the package
     */
    private static void foundPackage(File location, String packageName) {

        System.out.println();
        System.out.println(location + ": " + packageName);

        found = true;
    }

    /**
     * Prints out information on a location where we've found the class.
     */
    private static void foundClass(File location, String className, long lastModified, long fileSize) {
        String modified;
        if (lastModified == -1) {
            modified = "<not available>";
        }
        else {
            modified = cFormat.format(new Date(lastModified));
        }

        String size;
        if (fileSize == -1L) {
            size = "<not available>";
        }
        else {
            size = String.valueOf(fileSize);
        }

        System.out.println();
        System.out.println(location + ": " + className);
        System.out.println("   last modified: " + modified);
        System.out.println("   size in bytes: " + size);

        found = true;
    }
}

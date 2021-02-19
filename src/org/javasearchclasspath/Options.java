/**
 * Copyright 2021 by Carl Grundstrom. All rights reserved
 * <p>
 * License: Apache License V2.0. See https://www.apache.org/licenses/LICENSE-2.0
 */
package org.javasearchclasspath;

import java.util.*;

/**
 * Java equivalent of the C RTL routine getopt(). Used to parse command line options
 * containing UNIX-style switches.
 */
public class Options {
    private int argsIndex;
    private int optionIndex;
    private String value;
    private String[] args;
    private String control;

    private static final int advanceArgsIndex = 0;
    private static final int advanceOptionIndex = 1;

    /**
     * Initializes the options parsing from an array of arguments
     *
     * @param args The args parameter passed to main()
     * @param control A getopt style options format control, for example:
     * "abc:d", where 'a', 'b', and 'd' are simple options, and
     * 'c' is an option that takes an argument.
     */
    public Options(String[] args, String control) {
        this.args = args;
        this.control = control;
        argsIndex = 0;
        optionIndex = 1;
    }

    /**
     * Initializes the options parsing from a list of arguments
     *
     * @param list A list of arguments
     * @param control A getopt style options format control, for example:
     * "abc:d", where 'a', 'b', and 'd' are simple options, and
     * 'c' is an option that takes an argument.
     */
    public Options(List list, String control) {
        int count = list.size();
        args = new String[count];
        Iterator it = list.listIterator();
        for (int i = 0; i < count; i++) {
            args[i] = (String)it.next();
        }
        this.control = control;
        argsIndex = 0;
        optionIndex = 1;
    }

    /**
     * Returns the argument list. Useful when the List constructor is used
     * and you want to get parameters with getIndex().
     */
    public String[] getArgumentList() {
        return args;
    }

    /**
     * Indicates whether there are more options available
     *
     * @returns true if there are more options, false otherwise
     */
    public boolean hasNext() {
        /*
         * If the args index is past the end of the args vector, then
         * we have no more options
         */
        if (argsIndex == args.length) {
            return false;
        }
        String arg = args[argsIndex];
        int len = arg.length();

        /*
         * If we are in the middle of parsing options from a argument, and
         * we have more characters available, then we have more options.
         */
        if (optionIndex > 1 && optionIndex < len) {
            return true;
        }

        /*
         * If the next argument starts with a '-' and has additional
         * characters, then we have more options
         */
        if (arg.startsWith("-") && len > 1) {
            return true;
        }

        /*
         * We have an argument that does not start with '-', so we are done
         * parsing options
         */
        return false;
    }

    /**
     * Gets the next option, which is a single character. Also sets the value of the option
     * for later retrieval with getValue() if the control string specifies specifies that the
     * current option has a value.
     *
     * @returns The next option (a single case-sensitive character)
     */
    public char next() throws Exception {
        if (!hasNext()) {
            throw new Exception("no more options");
        }

        char option;
        value = null;
        String arg = args[argsIndex];
        int len = arg.length();

        /*
         * Get the next option
         */
        option = arg.charAt(optionIndex);
        if (option == ':') {
            throw new Exception("':' is not a valid option");
        }
        int i = control.indexOf(option);
        if (i == -1) {
            throw new Exception("'" + option + "' is not a valid option");
        }

        /*
         * If this argument should have a value, then look for it
         */
        boolean hasValue = control.length() > i + 1 && control.charAt(i + 1) == ':';
        if (hasValue) {
            /*
             * If we have no more characters in this argument, then the value for this
             * option is in the next argument
             */
            optionIndex++;
            if (optionIndex == len) {
                argsIndex++;
                if (argsIndex == args.length) {
                    throw new Exception("option '" + option + "' requires an argument");
                }
                value = args[argsIndex];
            }
            else {
                /*
                 * Get the value from the remaining characters in this argument
                 */
                value = arg.substring(optionIndex);
            }
        }

        /*
         * If there are no more options, move to the next argument,
         * otherwise advance the options index.
         */
        if (hasValue || optionIndex + 1 == len) {
            argsIndex++;
            optionIndex = 1;
        }
        else {
            optionIndex++;
        }

        return option;
    }

    /**
     * Gets the value associated with the current option.
     *
     * @returns A string value associated with the option or null if there is no value
     * associated with the current option
     */
    public String getValue() {
        return value;
    }

    /**
     * Gets the index of the first argument following the options.
     *
     * @returns The index of the first argument
     */
    public int getIndex() {
        return argsIndex;
    }

    /**
     * Test driver and sample code
     */
    public static void main(String[] args) {
        try {
            Options options = new Options(args, "abc:d");
            while (options.hasNext()) {
                switch (options.next()) {
                    case 'a':
                        System.err.println("option a");
                        break;

                    case 'b':
                        System.err.println("option b");
                        break;

                    case 'c':
                        System.err.println("option c: " + options.getValue());
                        break;

                    case 'd':
                        System.err.println("option d");
                        break;
                }
            }
            for (int i = options.getIndex(); i < args.length; i++) {
                System.err.println("arg: " + args[i]);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}

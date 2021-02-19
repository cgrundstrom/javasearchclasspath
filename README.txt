JavaSearchClassPath

JavaSearchClassPath searches the Java CLASSPATH for a class.  It is useful for discovering which jar file contains a
specified java class.

OS Support

JavaSearchClassPath works under Linux and MacOS. You can also use it on Windows if you are using cygwin. It has
not been ported the Windows CMD shell.

Building

Build using Apache Ant
1. Install Apache Ant
2. cd to the javasearchclasspath directory
3. Type "ant"

Usage

To use, add the bin directory to your PATH, set your classpath using the "setcp" script, then run javasearchclasspath.

Example:
  PATH=$HOME/projects/javasearchclasspath/bin:$PATH
  export CLASSPATH=
  cd $HOME/projects
  . setcp
  javasearchclasspath org.javasearchclasspath.JavaSearchClassPath

It may be convenient to add an alias for running javasearchclasspath

  alias jscp=javasearchclasspath

License

JavaSearchClassPath is licensed using Apache License V2.0. See https://www.apache.org/licenses/LICENSE-2.0


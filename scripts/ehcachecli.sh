#!/bin/bash

#
# All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
#

case "$1" in
  ""|"--help"|"-h"|"-?")
    echo "Syntax: $0 [cachePrint|cacheSize|cacheClear|cacheSelectiveClear|cacheInspect|tcPing] [arguments.....]"
    echo "cachePrint - Prints cache elements for a given cache."
    echo "cacheSize - Prints the total number of cache entries in each cache in a continous loop."
    echo "cacheClear - Clear 1 or more cache entries in specified cache or all caches."
    echo "cacheSelectiveClear - Clear all cache entries that match the date filter query"
    echo "cacheInspect - Inspect cache objects (keys and values) stored in cache and print types and size statistics"
    echo "cacheMgrConnect - Simple cache manager instantiation with no operation performed (useful to see caches in TMC for example)"
    echo "cacheRefresh - Get all objects in cache, and re-put them (hence refreshing creation time and expirations...)"
    echo "tcPing - Health Check of the cluster"
    exit
    ;;
  "tcPing")
    echo "For tcPing, it's best to use the pre-configured ehcacheping.sh script"
    exit;;
esac

# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

PRGDIR=`dirname "$PRG"`
BASEDIR=`cd "$PRGDIR/.." > /dev/null; pwd`

if [ -f "${BASEDIR}/bin/setenv-cli.sh" ]; then
  . "${BASEDIR}/bin/setenv-cli.sh"
fi

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false;
darwin=false;
case "`uname`" in
  CYGWIN*) cygwin=true ;;
  Darwin*) darwin=true
           if [ -z "$JAVA_VERSION" ] ; then
             JAVA_VERSION="CurrentJDK"
           else
             echo "Using Java version: $JAVA_VERSION"
           fi
           if [ -z "$JAVA_HOME" ] ; then
             JAVA_HOME=/System/Library/Frameworks/JavaVM.framework/Versions/${JAVA_VERSION}/Home
           fi
           ;;
esac

if [ -z "$JAVA_HOME" ] ; then
  if [ -r /etc/gentoo-release ] ; then
    JAVA_HOME=`java-config --jre-home`
  fi
fi

# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin ; then
  [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
  [ -n "$CLASSPATH" ] && CLASSPATH=`cygpath --path --unix "$CLASSPATH"`
fi

# If a specific java binary isn't specified search for the standard 'java' binary
if [ -z "$JAVACMD" ] ; then
  if [ -n "$JAVA_HOME"  ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
      # IBM's JDK on AIX uses strange locations for the executables
      JAVACMD="$JAVA_HOME/jre/sh/java"
    else
      JAVACMD="$JAVA_HOME/bin/java"
    fi
  else
    JAVACMD=`which java`
  fi
fi

if [ ! -x "$JAVACMD" ] ; then
  echo "Error: JAVA_HOME is not defined correctly." 1>&2
  echo "  We cannot execute $JAVACMD" 1>&2
  exit 1
fi

if [ -z "$LIBS" ]
then
  LIBS="$BASEDIR"/libs
fi

if [ -z "$CONFIG" ]
then
  CONFIG="$BASEDIR"/config
fi

CLASSPATH=$CLASSPATH_PREFIX:"$CONFIG":"$LIBS"/*

# For Cygwin, switch paths to Windows format before running java
if $cygwin; then
  [ -n "$CLASSPATH" ] && CLASSPATH=`cygpath --path --windows "$CLASSPATH"`
  [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --path --windows "$JAVA_HOME"`
  [ -n "$HOME" ] && HOME=`cygpath --path --windows "$HOME"`
  [ -n "$BASEDIR" ] && BASEDIR=`cygpath --path --windows "$BASEDIR"`
  [ -n "$LIBS" ] && LIBS=`cygpath --path --windows "$LIBS"`
  [ -n "$CONFIG" ] && CONFIG=`cygpath --path --windows "$CONFIG"`
fi

JAVA_OPTS="${JAVA_OPTS} -Xms128m -Xmx256m -XX:MaxDirectMemorySize=10G"
JAVA_OPTS="${JAVA_OPTS} -Dehcache.config.path=${EHCACHE_CONFIG_PATH}"

#if not defined or does not exist, try in the base directory
if [ -f ${TC_LICENSEKEY} ]; then
  JAVA_OPTS="${JAVA_OPTS} -Dcom.tc.productkey.path=${TC_LICENSEKEY}"
fi

exec "$JAVACMD" $JAVA_OPTS \
  -classpath "$CLASSPATH" \
  -Dbasedir="$BASEDIR" \
  com.terracotta.tools.$@
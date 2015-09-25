#!/bin/sh

#
# All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
#

BASE_DIR=`dirname "$0"`/..

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false
case "`uname`" in
CYGWIN*) cygwin=true;;
esac

if test \! -d "${JAVA_HOME}"; then
  echo "$0: the JAVA_HOME environment variable is not defined correctly"
  exit 2
fi

# For Cygwin, convert paths to Windows before invoking java
if $cygwin; then
  [ -n "$BASE_DIR" ] && BASE_DIR=`cygpath -d "$BASE_DIR"`
fi

PERF_CLASSPATH=$(echo ${BASE_DIR}/lib/*.jar | tr ' ' ':')
PERF_CLASSPATH=${PERF_CLASSPATH}:${BASE_DIR}/config/
JAVA_OPTS="${JAVA_OPTS} -Xms128m -Xmx512m -XX:MaxDirectMemorySize=10G -Dcom.tc.productkey.path=${BASE_DIR}/config/terracotta-license.key"
dt=`date +%Y%m%d_%H%M%S`
# Execute Step 1 of Ping
${JAVA_HOME}/bin/java -cp $PERF_CLASSPATH  com.terracotta.tools.tcPing 1 $dt 10000
return_code=$?
if [ $return_code == 0 ]
then
# Execute Step 2 of Ping
	${JAVA_HOME}/bin/java -cp $PERF_CLASSPATH  com.terracotta.tools.tcPing 2 $dt 10000
        return_code=$?
        if [ $return_code == 0 ]
        then
# Step 2 succesful
        exit 0
# Step 2 failed
        else
        exit 1
        fi
else
# Step 1 failed
exit 1
fi

terracotta-cache-utils
======================

Operations tool set for Terracotta via ehcache. 

This package contains handy tools to help operate teams to answer typical queries like
1. How many objects are in my cache?
2. Print all keys(strings) and possible values (strings)
3. Can you check if this business key exists.


Build
The project is maven based and have few profiles for various versions of ehcache / terracotta. Review the pom.xml for the profiles and build.

To create a tar package
mvn clean install 

this will create a ehcache-tools-bin.tar.gz which can be deployed on any host. 


Installation
1. Unzip ehcache-tools-bin.tar.gz to any directory. (TOOLS_HOME). 
2. Copy terracotta license file into TOOLS_HOME/config
3. Edit/Copy the ehcache.xml file into TOOLS_HOME/config. 


Tools Provided

ehcacheping : Ping utility to check health of terracotta cluster. This is a 2 step process. Loads abut 10K entries to a cache , disconnect and then read 10K entires back and verifies. 

Usage tcPing  [1/2] [unique id] [number of elements]
tcPing needs to be executed in a 2 step process. The first argument 1 is to load and 2 is retrieve and avalidate


ehcachecli is a command line cli with following utilities

./ehcachecli.sh -h
Syntax: ./ehcachecli.sh [cacheKeyValuePrint|cacheKeysPrint|cacheSize] [arguments.....]
cacheKeyValuePrint - Prints the Keys and values (only string or list/string) for a given cache.
cacheKeysPrint - Prints the Keys in a cache or all caches.
cacheSize - Prints the total number of cache entries in each cache in a continous loop.
tcPing - Health Check of the cluster




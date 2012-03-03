#!/bin/sh
# This is a shell script to extract a single JVM stat from a CSV
# representing a set of JVM metrics
# Invoke this script with two parameters:
# jvm_get_stat.sh path_to_csv_file field_number
# where the CSV-file is produced by JmxServerMonitoring tool

LOG=$1
FIELD=$2
AWK_COMMAND="{print \$$FIELD}"
tail -1 $LOG | awk -F "[ \t;]+" "$AWK_COMMAND"

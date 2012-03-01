package org.romix.monitoring;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 *  Group of Garbage-Collection-Value 
 *  (different Garbage-Collection kinds) and CPU-time
 *  
 *  @author romix
 */
class GarbageCollectionGroup {
	Date dateTime = new Date();
	List<GarbageCollectionSingle> gcSingles = new ArrayList<GarbageCollectionSingle>();
	double gcTimePercentSum;
	long cpuTimePercent;
}

package org.romix.monitoring;

import java.util.HashMap;
import java.util.Map;

/***
 * Access parameters and collected statistics for a single server
 * @author romix
 *
 */
class ServerData {
	String serverNameUndUrl;
	String serverName;
	String url;
	String usr;
	String pwd;
	Map<String, Long[]> lastMeasurement = new HashMap<String, Long[]>();
	GarbageCollectionGroup gcGroup = null;
	AttributeValueAndName[] attributes = null;
}

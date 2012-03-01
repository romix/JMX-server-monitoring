package org.romix.monitoring;

import java.io.*;
import java.lang.management.*;
import java.net.MalformedURLException;
import java.text.*;
import java.util.*;
import javax.management.*;
import javax.management.openmbean.CompositeData;
import javax.management.remote.*;
import javax.naming.Context;

/****
 * This is a small command-line utility for collecting different JVM statistics
 * via JMX interface.
 * 
 * It allows for collections of such stats like
 *  - CPU time (in %)
 *  - GC time (in %)
 *  - custom JVM attributes, e.g. related to used and committed heap memory, etc
 *  
 * To collect info about used and committed heap memory, define the following
 * attributes in the properties file:
 * 
 * attr1=; Used JVM Mem; HeapMemoryUsage.used; java.lang:type=Memory
 * attr2=; Total JVM Mem; HeapMemoryUsage.max; java.lang:type=Memory
 * attr3=; Init JVM Mem; HeapMemoryUsage.init; java.lang:type=Memory
 * attr4=; Committed JVM Mem; HeapMemoryUsage.committed; java.lang:type=Memory
 *  
 *  
 * The utility is based on the source code provided here by Torsten Horn:
 * http://www.torsten-horn.de/techdocs/jmx-gc.htm
 * 
 * @author romix
 *
 */
public class JmxServerMonitoring
{
   static final String HELP_TEXT =
      "JmxServerMonitoring:\n" +
      "  Provides procentual GC and other JVM stats via JMX.\n" +
      "Different results can be shown:\n" +
      "  'console=true':\n" +
      "     Output to console.\n" +
      "  'nagiosfile=JmxServerMonitoring.nagios.txt':\n" +
      "     Only last results (e.g. for Nagios).\n" +
      "  'csvfile=JmxServerMonitoring.csv':\n" +
      "     All results (.csv-Datei, e.g. for Excel).\n" +
      "  'errorfile=JmxServerMonitoring.error.log':\n" +
      "     File for error messages (e.g. Exceptions).\n" +
      "  'periodseconds=10':\n" +
      "     Mesurements interval in seconds.\n" +
      "You can provide as a URL host address or IP address, followed by a port number. " +
      "You can monitor a single JVM or multiple JVMs at the same time:\n" +
      "  'url=localhost:8686 usr=admin pwd=adminadmin':\n" +
      "     One single server (e.g. with GlassFish port number).\n" +
      "  'url=srv1:7091,srv2:7092,srv3:7093' usr=xy pwd=yz':\n" +
      "     Three servers with the same user name and same password.\n" +
      "  'url=srv1:7091,srv2:7092 usr=u1,u2 pwd=p1,p2':\n" +
      "     Two servers with different usernames/passwords.\n" +
      "  'usr=username pwd=password':\n" +
      "     Only required, if authentication is enabled.\n" +
      "Parameters can be passed via command-line or via  a properties file :\n" +
      "  'propfile=JmxServerMonitoring.properties':\n" +
      "     Path to the properties file.\n" +
      "Two examples of invocation:\n" +
      "  java JmxServerMonitoring propfile=JmxServerMonitoring.properties\n" +
      "  java JmxServerMonitoring url=localhost:8686 console=true csvfile=JmxServerMonitoring.csv\n";
   static final String KEY_PROPFILE       = "propfile";
   static final String KEY_PERIODSECONDS  = "periodseconds";
   static final String KEY_SERVERNAME     = "servername";
   static final String KEY_URL            = "url";
   static final String KEY_USR            = "usr";
   static final String KEY_PWD            = "pwd";
   static final String KEY_CONSOLE        = "console";
   static final String KEY_ALLGCVALUES    = "allgcvalues";
   static final String KEY_NAGIOSFILE     = "nagiosfile";
   static final String KEY_CSVFILE        = "csvfile";
   static final String KEY_ERRORFILE      = "errorfile";
   static final String KEY_ATTR           = "attr";
   static final String DFLT_PERIODSECONDS = "10";
   static final String DFLT_PROPFILE      = "JmxServerMonitoring.properties";
   static final String DFLT_NAGIOSFILE    = "JmxServerMonitoring.nagios.txt";
   static final String DFLT_ERRORFILE     = "JmxServerMonitoring.error.log";
   static final String DFLT_CONSOLE       = "true";
   static final SimpleDateFormat YYYYMMDD_HHMMSS_STD = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
   static final SimpleDateFormat YYYYMMDD_HHMMSS_NAG = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
   static final DecimalFormat    DECIMAL_FORMAT1     = new DecimalFormat( "0.0" );
   static final DecimalFormat    DECIMAL_FORMAT2     = new DecimalFormat( "0.00" );

   /***
    * Main entry point
    * 
    * @param args
    * @throws Exception
    */
   public static void main( String[] args ) throws Exception {
      Properties props = readProperties( args, KEY_PROPFILE, DFLT_PROPFILE, new String[] {
            KEY_PERIODSECONDS, DFLT_PERIODSECONDS, KEY_CONSOLE, DFLT_CONSOLE,
            KEY_NAGIOSFILE, DFLT_NAGIOSFILE, KEY_ERRORFILE, DFLT_ERRORFILE } );

		int periodSeconds = Math.max(
				Integer.parseInt(props.getProperty(KEY_PERIODSECONDS)), 1);
		String serverName = props.getProperty(KEY_SERVERNAME);
		String url = props.getProperty(KEY_URL);
		String usr = props.getProperty(KEY_USR);
		String pwd = props.getProperty(KEY_PWD);
		String nagiosFile = props.getProperty(KEY_NAGIOSFILE);
		String csvFile = props.getProperty(KEY_CSVFILE);
		String errorFile = props.getProperty(KEY_ERRORFILE);
		String s = props.getProperty(KEY_CONSOLE);
		boolean console = s != null
				&& (s.equals("1") || s.equalsIgnoreCase("true"));
		s = props.getProperty(KEY_ALLGCVALUES);
		boolean allGcValues = s != null
				&& (s.equals("1") || s.equalsIgnoreCase("true"));

		System.out.println("JmxServerMonitoring (periodseconds="
				+ periodSeconds + ", servername=" + serverName + ", url=" + url
				+ ", usr=" + usr + ", nagiosfile=" + nagiosFile + ", csvfile="
				+ csvFile + ", errorfile=" + errorFile + ")\n");

		if (url == null || url.trim().length() <= 0) {
			System.out.println("Error: imcomplete parameters.\n");
			System.out.println(HELP_TEXT);
			System.exit(255);
		}

		ServerData[] serverDataArr = convertSrvParameter(serverName, url, usr,
				pwd);
		AttributeValueAndName[] attributeNames = convertAttrParameter(props,
				KEY_ATTR);
		writeJmxServerMonitoring(periodSeconds, serverDataArr, attributeNames,
				console, allGcValues, nagiosFile, csvFile, errorFile);
   }

   /**
    * Splitting of  server-Parameter into multiple servers
    * @param serverName
    * @param url
    * @param usr
    * @param pwd
    * @return
    */
	static ServerData[] convertSrvParameter(String serverName, String url,
			String usr, String pwd) {
		if (url == null || url.trim().length() <= 0)
			return null;
		String[] urlArr = url.split(",|;|\\s");
		String[] usrArr = (usr != null) ? usr.split(",|;|\\s") : null;
		String[] pwdArr = (pwd != null) ? pwd.split(",|;|\\s") : null;
		String[] serverNameArr = (serverName != null) ? serverName
				.split(",|;|\\s") : null;
		boolean sn = serverName != null
				&& serverNameArr.length == urlArr.length;
		boolean up = usr != null && usrArr.length == urlArr.length
				&& pwd != null && pwdArr.length == urlArr.length;
		ServerData[] serverDataArr = new ServerData[urlArr.length];
		for (int i = 0; i < serverDataArr.length; i++) {
			serverDataArr[i] = new ServerData();
			serverDataArr[i].url = urlArr[i];
			serverDataArr[i].usr = (up) ? usrArr[i] : usr;
			serverDataArr[i].pwd = (up) ? pwdArr[i] : pwd;
			serverDataArr[i].serverName = (sn) ? serverNameArr[i] : null;
			serverDataArr[i].serverNameUndUrl = (serverDataArr[i].serverName != null) ? serverDataArr[i].serverName
					+ "-" + serverDataArr[i].url
					: serverDataArr[i].url;
			if (serverDataArr[i].serverName == null) {
				serverDataArr[i].serverName = serverDataArr[i].url;
			}
		}
		return serverDataArr;
	}

   /***
    * Splitting of optional additional MBean-attribute queries
    * 
    * Attribute names can have a form:
    * name1.name2.name3.etc
    */
	static AttributeValueAndName[] convertAttrParameter(Properties props,
			String key) {
		List<AttributeValueAndName> attributeNameList = new ArrayList<AttributeValueAndName>();
		for (int i = 1; i < 1000; i++) {
			String s = props.getProperty(key + i);
			if (s == null || s.trim().length() <= 0)
				continue;
			String[] ss = s.split(";");
			if (ss == null || ss.length < 4)
				break;
			for (int j = 0; j < ss.length; j++)
				if (ss[j] != null)
					ss[j] = ss[j].trim();
			AttributeValueAndName attributeName = new AttributeValueAndName();
			attributeName.diff = ss[0] != null
					&& ss[0].toLowerCase().startsWith("diff");
			attributeName.title = ss[1];
			attributeName.attributeName = ss[2];
			attributeName.objectName = ss[3];
			if (ss.length > 4)
				attributeName.methodName = ss[4];
			if (ss.length > 5) {
				attributeName.methodParms = new String[ss.length - 5];
				System.arraycopy(ss, 5, attributeName.methodParms, 0,
						attributeName.methodParms.length);
			}
			attributeNameList.add(attributeName);
		}
		return attributeNameList
				.toArray(new AttributeValueAndName[attributeNameList.size()]);
	}

   /***
    * Loop for collecting Garbage-Collection and other JVM statistics and writing output
    * 
    * @param periodSeconds
    * @param serverDataArr
    * @param attributeNames
    * @param showConsole
    * @param writeAllGcValues
    * @param nagiosFile
    * @param csvFile
    * @param errorFile
    */
	static void writeJmxServerMonitoring(int periodSeconds,
			ServerData[] serverDataArr, AttributeValueAndName[] attributeNames,
			boolean showConsole, boolean writeAllGcValues, String nagiosFile,
			String csvFile, String errorFile) {
		long periodTime = (new Date()).getTime();
		JMXConnector jmxConnector = null;

		// Loop with a given time intervals
		while (true) {
			// Iterate over all servers
			for (ServerData serverData : serverDataArr) {
				try {
					// JMX- und MBeanServer-Connection:
					jmxConnector = getJMXConnector(serverData.url,
							serverData.usr, serverData.pwd);
					MBeanServerConnection mBeanServerConn = jmxConnector
							.getMBeanServerConnection();
					// Read GC statistics
					serverData.gcGroup = getGarbageCollectionGroup(
							periodSeconds, serverData.lastMeasurement,
							mBeanServerConn);
					// Read additional MBean-Attributes
					serverData.attributes = getAttributes(attributeNames,
							periodSeconds, serverData.lastMeasurement,
							mBeanServerConn);
				} catch (Exception ex) {
					serverData.lastMeasurement.clear();
					serverData.gcGroup = null;
					serverData.attributes = attributeNames;
					String s = YYYYMMDD_HHMMSS_STD.format(new Date())
							+ ", Url=" + serverData.url + ": ";
					System.out.println(s);
					System.out.println(ex);
					writeErrorFile(s, ex, errorFile);
				} finally {
					try {
						if (jmxConnector != null)
							jmxConnector.close();
					} catch (Exception ex) {/* ok */
					}
					jmxConnector = null;
				}
			}
			// Write collected statistics
			writeConsole(serverDataArr, showConsole);
			writeNagiosFile(serverDataArr, nagiosFile);
			writeCsvFileOneForAllServers(serverDataArr, csvFile);
			writeCsvFilePerServerWithDifferentGcValues(serverDataArr, csvFile,
					writeAllGcValues);
			// Time interval
			periodTime += periodInSeconds(periodSeconds) * 1000;
			long waitMilliseconds = periodTime - (new Date()).getTime();
			if (waitMilliseconds > 0) {
				try {
					Thread.sleep(waitMilliseconds);
				} catch (InterruptedException ex) {/* ok */

				}
			}
		}
	}

   /***
    * Querying of a group of Garbage-Collection-statistics from a single server
    * @param periodSeconds
    * @param lastMeasurement
    * @param mBeanServerConn
    * @return
    * @throws Exception
    */
	static GarbageCollectionGroup getGarbageCollectionGroup(int periodSeconds,
			Map<String, Long[]> lastMeasurement,
			MBeanServerConnection mBeanServerConn) throws Exception {
		// Read a previous uptime of the JVM from Remote-Runtime-MXBean:
		long rtUptimeMs = getRuntimeMXBeanFromRemote(mBeanServerConn)
				.getUptime();
		// Read GarbageCollector-MXBeans from Remote:
		List<GarbageCollectorMXBean> gcMXBeans = getGarbageCollectorMXBeansFromRemote(mBeanServerConn);
		// Different kinds of Garbage-Collections:
		GarbageCollectionGroup gcGroup = new GarbageCollectionGroup();
		for (GarbageCollectorMXBean gc : gcMXBeans) {
			GarbageCollectionSingle gcSingle = new GarbageCollectionSingle();
			gcSingle.gcName = gc.getName();
			if (gcSingle.gcName != null && gcSingle.gcName.indexOf("Young") > 0) {
				gcSingle.gcName = gcSingle.gcName.substring(gcSingle.gcName
						.indexOf("Young"));
			}
			if (gcSingle.gcName != null && gcSingle.gcName.indexOf("Old") > 0) {
				gcSingle.gcName = gcSingle.gcName.substring(gcSingle.gcName
						.indexOf("Old"));
			}
			Long[] gcLast = lastMeasurement.get(gcSingle.gcName);
			if (gcLast != null) {
				gcSingle.gcCountPerPeriod = gc.getCollectionCount()
						- gcLast[0].longValue();
				gcSingle.gcTimePercent = ((gc.getCollectionTime() - gcLast[1]
						.longValue()) / (periodInSeconds(periodSeconds))) / 10.;
			}
			if (gcLast == null || gcSingle.gcCountPerPeriod < 0
					|| gcSingle.gcTimePercent < 0) {
				// First time query (or Server-Reboot):
				gcSingle.gcCountPerPeriod = gc.getCollectionCount()
						* periodInSeconds(periodSeconds) * 1000 / rtUptimeMs;
				gcSingle.gcTimePercent = (gc.getCollectionTime() * 1000 / rtUptimeMs) / 10.;
			}
			lastMeasurement.put(
					gcSingle.gcName,
					new Long[] { new Long(gc.getCollectionCount()),
							new Long(gc.getCollectionTime()) });
			gcGroup.gcSingles.add(gcSingle);
			gcGroup.gcTimePercentSum += gcSingle.gcTimePercent;
		}
		// CPU-Time:
		gcGroup.cpuTimePercent = calculateCpuTimePercent(rtUptimeMs,
				lastMeasurement, mBeanServerConn);
		return gcGroup;
	}

	private static int periodInSeconds(int periodSeconds) {
		return periodSeconds;
	}

   /***
    * CPU-Time
    * @param rtUptimeMs
    * @param lastMeasurement
    * @param mBeanServerConn
    * @return
    * @throws Exception
    */
	static int calculateCpuTimePercent(long rtUptimeMs,
			Map<String, Long[]> lastMeasurement,
			MBeanServerConnection mBeanServerConn) throws Exception {
		final String CPUTIME_ATTRIBUTENAME = "ProcessCpuTime";
		final String CPUTIME_OBJECTNAME = "java.lang:type=OperatingSystem";
		final String CPUTIME_KEY = CPUTIME_ATTRIBUTENAME + "::"
				+ CPUTIME_OBJECTNAME;
		try {
			Long cpuTime = (Long) mBeanServerConn.getAttribute(new ObjectName(
					CPUTIME_OBJECTNAME), CPUTIME_ATTRIBUTENAME);
			if (cpuTime == null)
				return -1;
			Long[] lastCpuTimeVals = lastMeasurement.get(CPUTIME_KEY);
			lastMeasurement.put(CPUTIME_KEY, new Long[] { new Long(rtUptimeMs),
					cpuTime });
			OperatingSystemMXBean op = getOperatingSystemMXBeanFromRemote(mBeanServerConn);
			long cpuCount = Math.max(1, op.getAvailableProcessors());
			long lastRtUptimeMs = 0;
			long lastCpuTime = 0;
			if (lastCpuTimeVals != null && lastCpuTimeVals.length > 1) {
				lastRtUptimeMs = lastCpuTimeVals[0].longValue();
				lastCpuTime = lastCpuTimeVals[1].longValue();
			}
			return (int) Math.min(99, (cpuTime.longValue() - lastCpuTime)
					/ ((rtUptimeMs - lastRtUptimeMs) * cpuCount * 10000));
		} catch (Exception ex) {
			return -1;
		}
	}

   /***
    * Additional  MBean-Attribute queries
    * @param attributeNames
    * @param periodSeconds
    * @param lastMeasurement
    * @param mBeanServerConn
    * @return
    * @throws Exception
    */
	static AttributeValueAndName[] getAttributes(
			AttributeValueAndName[] attributeNames, int periodSeconds,
			Map<String, Long[]> lastMeasurement,
			MBeanServerConnection mBeanServerConn) throws Exception {
		if (attributeNames == null || attributeNames.length <= 0)
			return null;
		List<AttributeValueAndName> attributesList = new ArrayList<AttributeValueAndName>();
		for (AttributeValueAndName attrNam : attributeNames) {
			boolean attrFound = false;
			// Get all object names matching a pattern
			Set<ObjectName> objectNames = mBeanServerConn.queryNames(
					new ObjectName(attrNam.objectName.trim()), null);
			for (ObjectName objectName : objectNames) {
				Object obj = null;
				// Handle invocation of operations on attributes
				if (attrNam.attributeName.trim().equalsIgnoreCase("invoke")) {
					obj = invoke(attrNam.methodName, attrNam.methodParms,
							objectName, mBeanServerConn);
				} else {
					// Handle reading of attribute values. Hierarchical names
					// like x.y.z are supported
					// The attribute value of the last element in a compound
					// name is supposed to be integer or long
					String attrName = attrNam.attributeName.trim();
					String[] attrNameParts = attrName.split("\\.");
					obj = mBeanServerConn.getAttribute(objectName,
							attrNameParts[0]);
					for (int i = 1; i < attrNameParts.length; ++i) {
						obj = ((CompositeData) obj).get(attrNameParts[i]);
					}
				}
				AttributeValueAndName attrVal = new AttributeValueAndName();
				attrVal.diff = attrNam.diff;
				attrVal.title = attrNam.title;
				attrVal.attributeName = attrNam.attributeName;
				attrVal.objectName = "" + objectName;
				long actVal = -1;
				try {
					actVal = Long.parseLong("" + obj);
				} catch (Exception ex) {/* ok */
				}
				if (!attrVal.diff || actVal < 0 || periodSeconds <= 0) {
					// No difference based output:
					attrVal.value = (obj instanceof Double) ? DECIMAL_FORMAT2
							.format(obj) : ("" + obj);
				} else {
					// difference based output and conversion into per second
					// values:
					String key = attrVal.attributeName + "::"
							+ attrVal.objectName;
					Long[] lastVal = lastMeasurement.get(key);
					lastMeasurement.put(key, new Long[] { new Long(actVal) });
					long v = 0;
					if (lastVal != null && lastVal[0] != null
							&& (v = actVal - lastVal[0].longValue()) >= 0) {
						// There is a valid last value:
						v = v / periodSeconds / 6;// ????
					} else {
						// Read previous up-time of the JVM from
						// Remote-Runtime-MXBean:
						long rtUptimeMs = getRuntimeMXBeanFromRemote(
								mBeanServerConn).getUptime();
						v = actVal * 10000 / rtUptimeMs;
					}
					attrVal.value = DECIMAL_FORMAT1.format(v / 10.);
				}
				attributesList.add(attrVal);
				attrFound = true;
			}
			if (!attrFound) {
				attributesList.add(attrNam);
			}
		}
		return attributesList.toArray(new AttributeValueAndName[attributesList
				.size()]);
	}

   /***
    * Invoke an MBean-Method
    * @param methodName
    * @param parms
    * @param on
    * @param mBeanServerConn
    * @return
    * @throws Exception
    */
	static Object invoke(String methodName, String[] parms, ObjectName on,
			MBeanServerConnection mBeanServerConn) throws Exception {
		if (methodName == null || on == null || mBeanServerConn == null)
			return null;
		Object[] oa = null;
		String[] sa = null;
		if (parms != null && parms.length >= 2) {
			final Map<String, Class<?>[]> PRIMITIVE_TYPEN = new HashMap<String, Class<?>[]>();
			PRIMITIVE_TYPEN.put("boolean", new Class<?>[] { boolean.class,
					Boolean.class });
			PRIMITIVE_TYPEN.put("int", new Class<?>[] { int.class,
					Integer.class });
			PRIMITIVE_TYPEN.put("long",
					new Class<?>[] { long.class, Long.class });
			PRIMITIVE_TYPEN.put("double", new Class<?>[] { double.class,
					Double.class });
			oa = new Object[parms.length / 2];
			sa = new String[parms.length / 2];
			for (int i = 0; i < parms.length - 1; i++) {
				// Are parameters of primitive types?
				Class<?>[] classForSigAndObj = PRIMITIVE_TYPEN.get(parms[i]);
				// Classes as Parameter-Types:
				if (classForSigAndObj == null) {
					classForSigAndObj = new Class<?>[2];
					try {
						classForSigAndObj[0] = Class.forName("java.lang."
								+ parms[i]);
					} catch (ClassNotFoundException ex) {
						classForSigAndObj[0] = Class.forName(parms[i]);
					}
					classForSigAndObj[1] = classForSigAndObj[0];
				}
				oa[i / 2] = classForSigAndObj[1].getConstructor(String.class)
						.newInstance(parms[++i]);
				sa[i / 2] = classForSigAndObj[0].getName();
			}
		}
		return mBeanServerConn.invoke(on, methodName, oa, sa);
	}

   /***
    * JMX-Connection
    * @param url
    * @param usr
    * @param pwd
    * @return
    * @throws MalformedURLException
    * @throws IOException
    */
	static JMXConnector getJMXConnector(String url, String usr, String pwd)
			throws MalformedURLException, IOException {
		String serviceUrl = "service:jmx:rmi:///jndi/rmi://" + url + "/jmxrmi";
		if (usr == null || usr.trim().length() <= 0 || pwd == null
				|| pwd.trim().length() <= 0) {
			return JMXConnectorFactory.connect(new JMXServiceURL(serviceUrl));
		}
		Map<String, Object> envMap = new HashMap<String, Object>();
		envMap.put("jmx.remote.credentials", new String[] { usr, pwd });
		envMap.put(Context.SECURITY_PRINCIPAL, usr);
		envMap.put(Context.SECURITY_CREDENTIALS, pwd);
		return JMXConnectorFactory.connect(new JMXServiceURL(serviceUrl),
				envMap);
	}

   /***
    * Read Runtime-MXBean from Remote
    * @param mBeanServerConn
    * @return
    * @throws IOException
    */
	static RuntimeMXBean getRuntimeMXBeanFromRemote(
			MBeanServerConnection mBeanServerConn) throws IOException {
		return ManagementFactory.newPlatformMXBeanProxy(mBeanServerConn,
				ManagementFactory.RUNTIME_MXBEAN_NAME, RuntimeMXBean.class);
	}

   /***
    * Read OperatingSystem-MXBean from Remote
    * @param mBeanServerConn
    * @return
    * @throws IOException
    */
	static OperatingSystemMXBean getOperatingSystemMXBeanFromRemote(
			MBeanServerConnection mBeanServerConn) throws IOException {
		return ManagementFactory.newPlatformMXBeanProxy(mBeanServerConn,
				ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME,
				OperatingSystemMXBean.class);
	}

   /***
    * Read GarbageCollector-MXBeans from Remote
    * @param mBeanServerConn
    * @return
    * @throws MalformedObjectNameException
    * @throws NullPointerException
    * @throws IOException
    */
	static List<GarbageCollectorMXBean> getGarbageCollectorMXBeansFromRemote(
			MBeanServerConnection mBeanServerConn)
			throws MalformedObjectNameException, NullPointerException,
			IOException {
		List<GarbageCollectorMXBean> gcMXBeans = new ArrayList<GarbageCollectorMXBean>();
		ObjectName gcAllObjectName = new ObjectName(
				ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*");
		Set<ObjectName> gcMXBeanObjectNames = mBeanServerConn.queryNames(
				gcAllObjectName, null);
		for (ObjectName on : gcMXBeanObjectNames) {
			GarbageCollectorMXBean gc = ManagementFactory
					.newPlatformMXBeanProxy(mBeanServerConn,
							on.getCanonicalName(), GarbageCollectorMXBean.class);
			gcMXBeans.add(gc);
		}
		return gcMXBeans;
	}

   /***
    * Output to console
    * @param serverDataArr
    * @param showConsole
    */
	static void writeConsole(ServerData[] serverDataArr, boolean showConsole) {
		if (serverDataArr == null || serverDataArr.length <= 0 || !showConsole)
			return;
		for (ServerData serverData : serverDataArr) {
			if (serverData.gcGroup == null)
				continue;
			System.out.print(YYYYMMDD_HHMMSS_STD
					.format(serverData.gcGroup.dateTime) + ": ");
			System.out.print(serverData.serverNameUndUrl + ": ");
			System.out
					.println("GarbageCollectionPercent = "
							+ DECIMAL_FORMAT1
									.format(serverData.gcGroup.gcTimePercentSum)
							+ " %");
		}
		for (ServerData serverData : serverDataArr) {
			if (serverData.gcGroup == null)
				continue;
			System.out.print(YYYYMMDD_HHMMSS_STD
					.format(serverData.gcGroup.dateTime) + ": ");
			System.out.print(serverData.serverNameUndUrl + ": ");
			System.out.println("CpuTimePercent = "
					+ serverData.gcGroup.cpuTimePercent + " %");
		}
		for (ServerData serverData : serverDataArr) {
			if (serverData.attributes == null
					|| serverData.attributes.length <= 0)
				continue;
			for (AttributeValueAndName attr : serverData.attributes) {
				if (attr.value == null || attr.value.length() <= 0
						|| attr.value.equals(AttributeValueAndName.ERR_VALUE))
					continue;
				System.out.print(YYYYMMDD_HHMMSS_STD.format(attr.dateTime)
						+ ": ");
				System.out.print(serverData.serverNameUndUrl + ": ");
				System.out.println(attr.title + " = " + attr.value);
			}
		}
		System.out.println();
	}

   /***
    * Write summary results into a file (e.g. for Nagios)
    * @param serverDataArr
    * @param nagiosFile
    */
	static void writeNagiosFile(ServerData[] serverDataArr, String nagiosFile) {
		if (serverDataArr == null || serverDataArr.length <= 0
				|| nagiosFile == null || nagiosFile.trim().length() <= 0)
			return;
		BufferedWriter out = null;
		try {
			Date dat = new Date();
			out = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(nagiosFile)));
			out.write("SecondsSince1970=" + (dat.getTime() / 1000));
			out.newLine();
			out.write("DateTime=" + YYYYMMDD_HHMMSS_NAG.format(dat));
			out.newLine();
			for (ServerData serverData : serverDataArr) {
				if (serverData.gcGroup == null)
					continue;
				out.write(serverData.serverName.replaceAll("[:-]", ".") + ".");
				out.write("GarbageCollectionPercent="
						+ DECIMAL_FORMAT1.format(
								serverData.gcGroup.gcTimePercentSum).replace(
								',', '.'));
				out.newLine();
			}
			for (ServerData serverData : serverDataArr) {
				if (serverData.gcGroup == null)
					continue;
				out.write(serverData.serverName.replaceAll("[:-]", ".") + ".");
				out.write("CpuTimePercent=" + serverData.gcGroup.cpuTimePercent);
				out.newLine();
			}
			for (ServerData serverData : serverDataArr) {
				if (serverData.attributes == null
						|| serverData.attributes.length <= 0)
					continue;
				for (AttributeValueAndName attr : serverData.attributes) {
					if (attr.value == null
							|| attr.value.length() <= 0
							|| attr.value
									.equals(AttributeValueAndName.ERR_VALUE))
						continue;
					out.write(serverData.serverName.replaceAll("[:-]", ".")
							+ ".");
					out.write(attr.title
							+ "="
							+ attr.value.replace(',', '.').replaceAll(
									"\r\n|\r|\n", ". "));
					out.newLine();
				}
			}
		} catch (Exception exWrite) {
			System.out.println("Error writing Nagios-file '" + nagiosFile
					+ "': " + exWrite);
		} finally {
			if (out != null)
				try {
					out.close();
				} catch (Exception exClose) {/* ok */
				}
		}
	}

   /***
    * Output into a single CSV-file (Comma Separated Values, e.g. for Excel):
    * Use a common CSV-file for all Servers (GC: only with summary values)
    * @param serverDataArr
    * @param csvFile
    */
   // 
	static void writeCsvFileOneForAllServers(ServerData[] serverDataArr,
			String csvFile) {
		if (serverDataArr == null || serverDataArr.length <= 0
				|| csvFile == null || csvFile.trim().length() <= 0)
			return;
		BufferedWriter out = null;
		try {
			boolean exists = (new File(csvFile)).exists();
			out = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(csvFile, true)));
			if (!exists) {
				out.write("Date/Time;");
				for (ServerData serverData : serverDataArr) {
					out.write(" "
							+ ((serverData != null) ? ("GC-" + serverData.serverName)
									: "?") + ";");
				}
				for (ServerData serverData : serverDataArr) {
					out.write(" "
							+ ((serverData != null) ? ("CPU-" + serverData.serverName)
									: "?") + ";");
				}
				for (ServerData serverData : serverDataArr) {
					if (serverData != null && serverData.attributes != null) {
						for (AttributeValueAndName attr : serverData.attributes) {
							out.write(" " + attr.title + "-"
									+ serverData.serverName + ";");
						}
					}
				}
				out.newLine();
			}
			out.write(YYYYMMDD_HHMMSS_STD.format(new Date()) + ";");
			for (ServerData serverData : serverDataArr) {
				double d = (serverData != null && serverData.gcGroup != null) ? serverData.gcGroup.gcTimePercentSum
						: -0.1;
				out.write(" " + DECIMAL_FORMAT1.format(d) + ";");
			}
			for (ServerData serverData : serverDataArr) {
				out.write(((serverData != null && serverData.gcGroup != null) ? (" " + serverData.gcGroup.cpuTimePercent)
						: " -0.1")
						+ ";");
			}
			for (ServerData serverData : serverDataArr) {
				if (serverData != null && serverData.attributes != null) {
					for (AttributeValueAndName attr : serverData.attributes) {
						out.write(" "
								+ attr.value.replaceAll("\r\n|\r|\n", ". ")
								+ ";");
					}
				}
			}
			out.newLine();
		} catch (Exception exWrite) {
			System.out.println("Error writing the CSV-file '" + csvFile + "': "
					+ exWrite);
		} finally {
			if (out != null)
				try {
					out.close();
				} catch (Exception exClose) {/* ok */
				}
		}
	}

   /***
    * Output into multiple CSV-files (Comma Separated Values, e.g. for Excel):
    * Output a separate CSV-file per server with all collected GC-values
    * 
    * @param serverDataArr
    * @param csvFileOhneUrl
    * @param writeAllGcValues
    */
	static void writeCsvFilePerServerWithDifferentGcValues(
			ServerData[] serverDataArr, String csvFileOhneUrl,
			boolean writeAllGcValues) {
		if (!writeAllGcValues || serverDataArr == null
				|| serverDataArr.length <= 0 || csvFileOhneUrl == null
				|| csvFileOhneUrl.trim().length() <= 0)
			return;
		for (ServerData serverData : serverDataArr) {
			String urlInsert = "-" + serverData.url.replace(':', '.');
			int e = csvFileOhneUrl.lastIndexOf('.');
			String csvFileMitUrl = (e > 0 && e < csvFileOhneUrl.length() - 1) ? csvFileOhneUrl
					.substring(0, e) + urlInsert + csvFileOhneUrl.substring(e)
					: csvFileOhneUrl + urlInsert + ".csv";
			BufferedWriter out = null;
			try {
				if (serverData.gcGroup == null
						|| serverData.gcGroup.gcSingles == null
						|| serverData.gcGroup.gcSingles.size() <= 0)
					continue;
				boolean exists = (new File(csvFileMitUrl)).exists();
				out = new BufferedWriter(new OutputStreamWriter(
						new FileOutputStream(csvFileMitUrl, true)));
				if (!exists) {
					out.write("Datum/Zeit; ");
					for (GarbageCollectionSingle gcSingle : serverData.gcGroup.gcSingles) {
						int n;
						String s = gcSingle.gcName;
						if (s == null)
							s = "";
						if ((n = s.lastIndexOf(" Collector")) > 1)
							s = s.substring(0, n);
						s = s.trim();
						if (s.length() > 0)
							s = s + "-";
						out.write(s + "CountPerPeriod; " + s + "TimePercent; ");
					}
					out.write("TimePercentSum;");
					out.newLine();
				}
				out.write(YYYYMMDD_HHMMSS_STD
						.format(serverData.gcGroup.dateTime) + "; ");
				for (GarbageCollectionSingle gcSingle : serverData.gcGroup.gcSingles) {
					out.write(gcSingle.gcCountPerPeriod + "; "
							+ DECIMAL_FORMAT1.format(gcSingle.gcTimePercent)
							+ "; ");
				}
				out.write(DECIMAL_FORMAT1
						.format(serverData.gcGroup.gcTimePercentSum) + "; ");
				out.newLine();
			} catch (Exception exWrite) {
				System.out.println("Error writing the CSV-file '"
						+ csvFileMitUrl + "': " + exWrite);
			} finally {
				if (out != null)
					try {
						out.close();
					} catch (Exception exClose) {/* ok */
					}
			}
		}
	}

   /**
    * Output Exceptions into an error file
    * @param s
    * @param ex
    * @param errorFile
    */
	static void writeErrorFile(String s, Exception ex, String errorFile) {
		if (errorFile == null || errorFile.trim().length() <= 0)
			return;
		BufferedWriter out = null;
		try {
			out = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(errorFile, true)));
			out.newLine();
			if (s != null)
				out.write(s);
			out.newLine();
			if (ex != null)
				out.write(ex.toString());
			out.newLine();
			out.close();
		} catch (Exception exWrite) {
			System.out.println("Error writing into error file '" + errorFile
					+ "': " + exWrite);
		} finally {
			if (out != null)
				try {
					out.close();
				} catch (Exception exClose) {/* ok */
				}
		}
	}

   /***
   	* Read a parameter into a Properties object:
    *  a) Parameters from a command-line (have a priority),
    *  b) Parameter from a properties file,
    *  c) Eventually, a default-Parameter (if parameter was not set explicitly).
    * If command-line defines a properties file, it should exist and should be readable
    * (Any error lead to error messages).
    * If not: It will look for a (default) propery file provided as a method parameter
    * If it does not exist, there will be no error message.
    * 
    * @param args
    * @param keyPropFile
    * @param propFileTry
    * @param defaultProps
    * @return
    * @throws Exception
    */
	static Properties readProperties(String[] args, String keyPropFile,
			String propFileTry, String[] defaultProps) throws Exception {
		Properties props = new Properties();
		String propFilePrio = null;
		String s;

		// If properties file name was provided as a command-line parameter, it
		// has a priority
		if (args != null && args.length > 0 && keyPropFile != null
				&& keyPropFile.trim().length() > 0
				&& args[0].toLowerCase().startsWith(keyPropFile + "=")) {
			propFilePrio = args[0].substring(keyPropFile.length() + 1);
		}

		// The properties file name passed as a method parameter will be used
		// only if this file exists:
		if (propFilePrio == null && propFileTry != null
				&& propFileTry.trim().length() > 0
				&& (new File(propFileTry)).exists()) {
			propFilePrio = propFileTry;
		}

		// If it was possible to find a property file name:
		// Read 'Key=Value' pairs from this property file (errors lead to error
		// messages):
		if (propFilePrio != null && propFilePrio.trim().length() > 0) {
			try {
				props.load(new FileInputStream(propFilePrio));
				System.out.println("Property-Datei: '" + propFilePrio + "'.");
			} catch (FileNotFoundException ex) {
				throw new Exception("Error: property file '" + propFilePrio
						+ "' fehlt: ", ex);
			} catch (IOException ex) {
				throw new Exception("Error reading a property file '"
						+ propFilePrio + "': ", ex);
			}
		}

		// Read 'Key=Value'-pairs from command line parameters
		// (they can override parameters from the property file):
		for (int i = 0; args != null && i < args.length; i++) {
			int delimPos = args[i].indexOf('=');
			if (delimPos > 0 && delimPos <= args[i].length() - 2) {
				props.put(args[i].substring(0, delimPos).trim().toLowerCase(),
						args[i].substring(delimPos + 1).trim());
			}
		}

		// Load optional default values:
		if (defaultProps != null && defaultProps.length / 2 > 0) {
			for (int i = 0; i < defaultProps.length / 2; i++) {
				if ((s = props.getProperty(defaultProps[i * 2])) == null
						|| s.trim().length() == 0) {
					props.put(defaultProps[i * 2], defaultProps[i * 2 + 1]);
				}
			}
		}
		return props;
	}
}


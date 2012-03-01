package org.romix.monitoring;

import java.util.Date;

/**
 * If quering additonal attributes: 
 * Name and value
 * @author romix
 *
 */
public class AttributeValueAndName {
	static final String ERR_VALUE = "-0,1";
	Date dateTime = new Date();
	boolean diff;
	String value = ERR_VALUE;
	String title;
	String attributeName;
	String objectName;
	String methodName;
	String[] methodParms;
}
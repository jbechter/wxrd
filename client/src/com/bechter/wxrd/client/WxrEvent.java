/**
 * Copyright 2006
 *
 * WxrEvent.java
 *
 * @author
 * @version
 */
package com.bechter.wxrd.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * @author joe
 *
 */
public class WxrEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    private static Map<String, String> codeMap = null;
    private static Map<String, String> counties = null;
    private static final SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
    private static final SimpleDateFormat tsdf = new SimpleDateFormat("HH:mm MMM dd yyyy");

    private List<String> locations = null;
    private String origin = null;
    private String eventType = null;
    private Date eventTime;
    private Date expiresTime;
    private String source = null;
    
    /**
     * Constructor for raw message
     * 
     * @param message
     */
    public WxrEvent() {
    }

    /**
     * 
     * @param message
     * @throws InvalidEventException
     */
    public void parse(String message) throws InvalidEventException {
        try {
            setSource("wxrd");
            StringTokenizer st = new StringTokenizer(message);
            setOrigin(st.nextToken());
            setEventType(st.nextToken());
            StringTokenizer ct = new StringTokenizer(st.nextToken(), "-");
            setLocations(new ArrayList<String>());
            while (ct.hasMoreTokens()) {
                getLocations().add(ct.nextToken());
            }
            String time = st.nextToken();
            int plus = time.indexOf("+");
                setEventTime(df.parse(time.substring(0, plus)));
            Calendar cal = Calendar.getInstance();
            cal.setTime(getEventTime());
            cal.add(Calendar.HOUR, Integer.parseInt(time.substring(plus + 1, plus + 3)));
            cal.add(Calendar.MINUTE, Integer.parseInt(time.substring(plus + 3, plus + 5)));
            setExpiresTime(cal.getTime());
        } catch (Exception e) {
            throw new InvalidEventException("Failed to parse raw message '" + message + "'", e);
        }
    }

    /**
     * Transforms an event code to its text description
     * 
     * @return
     * @throws InvalidEventException 
     */
    public String getEventTypeDescription() throws InvalidEventException {
        String description = getCodeMap().get(getEventType());
        return description == null ? "Unknown Event" : description;
    }

    /**
     * 
     * @return map of event codes and text descriptions
     * @throws InvalidEventException 
     */
    private Map<String, String> getCodeMap() throws InvalidEventException {
        if (codeMap == null)
            codeMap = populateCodeMap();
        return codeMap;
    }
    
    /**
     * Populates the map of event codes and text transalations
     * 
     * @return 
     * @throws InvalidEventException 
     */
    private Map<String, String> populateCodeMap() throws InvalidEventException {
        Map<String, String> map = new HashMap<String, String>();
        Properties properties = new Properties();
        InputStream is;
        is = Thread.currentThread().getContextClassLoader().getResourceAsStream("alerts.properties");
        try {
            properties.load(is);
            Enumeration keys = properties.keys();
            while (keys.hasMoreElements()) {
                String key = (String)keys.nextElement();
                map.put(key, properties.getProperty(key));
            }
        } catch (IOException e) {
            throw new InvalidEventException("Failed to load alerts.properties", e);
        }
        return map;
    }
    
    /**
     * 
     * @return text describing counties involved in the event
     * @throws InvalidEventException 
     */
    public String getLocationDescription() throws InvalidEventException {
        StringBuffer sb = new StringBuffer();
        List<String> locations = getLocations();
        for (int i = 0; i < locations.size(); i++) {
            String county = getCounties().get(locations.get(i));
            if (county == null)
                county = "(" + locations.get(i) + ")";
            sb.append(county);
            if (i < locations.size() - 2)
                sb.append(", ");
            else if (i < locations.size() - 1 && locations.size() > 1)
                sb.append(" and ");
        }
        if (locations.size() == 1)
            sb.append(" County");
        else
            sb.append(" Counties");
        return sb.toString();
    }
    
    /**
     * 
     * @return map of FIPS codes and county names for Ohio
     * @throws InvalidEventException 
     */
    private Map<String, String> getCounties() throws InvalidEventException {
        if (counties == null)
            counties = populateCounties();
        return counties;
    }
    
    /**
     * 
     * @return populated map of FIPS codes and county names
     * @throws InvalidEventException 
     */
    private Map<String, String> populateCounties() throws InvalidEventException {
        Map<String, String> map = new HashMap<String, String>();
        Properties properties = new Properties();
        InputStream is;
        is = Thread.currentThread().getContextClassLoader().getResourceAsStream("counties.properties");
        try {
            properties.load(is);
            Enumeration keys = properties.keys();
            while (keys.hasMoreElements()) {
                String key = (String)keys.nextElement();
                map.put(key, properties.getProperty(key));
            }
        } catch (IOException e) {
            throw new InvalidEventException("Failed to load counties.properties", e);
        }
        return map;
    }
    
    /**
     * 
     * @return
     */
    public String getOriginDescription() {
        String origin = getOrigin();
        if ("WXR".equals(origin))
            return "The National Weather Service";
        else if ("EAS".equals(origin))
            return "The Emergency Alert System";
        else if ("CIV".equals(origin))
            return ("Civil Authority");
        else if ("PEP".equals(origin))
            return "Primary Entry Point System";
        else 
            return origin; 
        
    }
    
    /**
     * 
     * @return
     */
    public boolean isActive() {
        Date now = new Date();
        return now.compareTo(getExpiresTime()) <= 0;
    }

    /**
     * @return Returns the eventTime.
     */
    public Date getEventTime() {
            return eventTime;
    }

    /**
     * @param eventTime The eventTime to set.
     */
    public void setEventTime(Date eventTime) {
            this.eventTime = eventTime;
    }

    /**
     * @return Returns the eventType.
     */
    public String getEventType() {
            return eventType;
    }

    /**
     * @param eventType The eventType to set.
     */
    public void setEventType(String eventType) {
            this.eventType = eventType;
    }

    /**
     * @return Returns the expiresTime.
     */
    public Date getExpiresTime() {
            return expiresTime;
    }

    /**
     * @param expiresTime The expiresTime to set.
     */
    public void setExpiresTime(Date expiresTime) {
            this.expiresTime = expiresTime;
    }

    /**
     * @return Returns the locations.
     */
    public List<String> getLocations() {
            return locations;
    }

    /**
     * @param locations The locations to set.
     */
    public void setLocations(List<String> locations) {
            this.locations = locations;
    }

    /**
     * @return Returns the origin.
     */
    public String getOrigin() {
            return origin;
    }

    /**
     * @param origin The origin to set.
     */
    public void setOrigin(String origin) {
            this.origin = origin;
    }

    /**
     * @return Returns the source.
     */
    public String getSource() {
            return source;
    }

    /**
     * @param source The source to set.
     */
    public void setSource(String source) {
            this.source = source;
    }

    /**
     * 
     */
    public String toString() {
        try {
            StringBuffer sb = new StringBuffer();
            sb.append(getOriginDescription());
            sb.append(" has issued a ");
            sb.append(getEventTypeDescription());
            sb.append(" for ");
            sb.append(getLocationDescription());
            if (getExpiresTime() != null) {
                sb.append(" until ");
                sb.append(tsdf.format(getExpiresTime()));
            }
            sb.append(".");
            return sb.toString();
        } catch (Exception e) {
            return e.toString();
        }
    }
    
    /**
     * 
     */
    public boolean equals(Object o) {
        if (o instanceof WxrEvent && o != null) {
            WxrEvent other = (WxrEvent)o;
            return ((getEventType() != null && getEventType().equals(other.getEventType())
                            || getEventType() == null && other.getEventType() == null)
                    && (getOrigin() != null && getOrigin().equals(other.getOrigin())
                            || getOrigin() == null && other.getOrigin() == null)
                    && (getLocations() != null && getLocations().equals(other.getLocations())
                            || getLocations() == null && other.getLocations() == null)
                    && (getEventTime() != null && other.getEventTime() != null 
                            && Math.abs(getEventTime().getTime() - other.getEventTime().getTime()) < 45000L
                            || getEventTime() == null && other.getEventTime() == null));
        } else
            return false;
    }
}

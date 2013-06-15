/*
 * WxrClient.java
 * 
 *   Java client that listens for messages from a wxrd server
 *   and sends email or short mail to slected addreses
 *   whenever a message is received.
 *   
 *   Copyright 2011, bechter.com - All Rights Reserved
 *   
 *      1. All files, software, schematics and designs are provided as-is with no warranty.
 *      2. All files, software, schematics and designs are for experimental/hobby use. 
 *         Under no circumstances should any part be used for critical systems where safety, 
 *         life or property depends upon it. You are responsible for all use.
 *      3. You are free to use, modify, derive or otherwise extend for your own non-commercial purposes provided
 *         1. No part of this software or design may be used to cause injury or death to humans or animals.
 *         2. Use is non-commercial.
 *         3. Credit is given to the author (i.e. portions © bechter.com), 
 *            and provide a link to this site (http://projects.bechter.com).
 *
 *   command line options:
 *   -h | --help     : print this message
 *   -t | --host     : hostname of host running wxrd (default is localhost
 *   -p | --port     : port on host running wxrd to connect to (default is 9317
 *   -e | --email    : email address (or comma-separated list of addresses) to email
 *   -s | --sms      : sms email gateway address (or comma-separated list of addresses) to sms
 *   -m | --mailhost : SMTP email host to use to send email
 *   -u | --mailuser : SMTP username used to send email
 *   -f | --from     : email address to appear in the "from" of the email/sms
 *   
 *   See: http://projects.bechter.com/wxrd/
 *   
 *   dependent libraries:
 *     log4j.jar : https://logging.apache.org/log4j/1.2/download.html
 *     activation.jar : http://www.oracle.com/technetwork/java/javase/index-jsp-136939.html
 *     javamail.jar : http://www.oracle.com/technetwork/java/javamail/index-138643.html
 *     
 *   requires Java 1.5 or higher
 */
package com.bechter.wxrd.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.Message.RecipientType;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.log4j.Logger;

/**
 *  WxrClient
 *
 */
public class WxrClient {
    private final Logger logger = Logger.getLogger(WxrClient.class);
    private int port = 9317;
    private String host = "localhost";
    private List<String> emailRecipients = null;
    private List<String> smsRecipients = null;
    private static WxrClient instance = null;
    private Socket socket = null;
    private static final DateFormat df = new SimpleDateFormat("HH:mm dd/MM/yyyy");
    private String mailHost = null;
    private String mailUser = null;
    private String mailTo = "wxrd@localhost";
    private String mailFrom = "wxrd@localhost";
    private boolean test = false;
    private static final int READ_SOCKET_TIMEOUT = 30000;

 
    /**
     * private constructor
     * 
     */
    private WxrClient() {
        logger.info("Creating WxrClient instance");
    }

    /**
     * run()
     * 
     *   called to begin listening to wxrd 
     *   
     */
    public void run() {
        if (logger.isDebugEnabled())
            logger.debug("run called");
        BufferedReader is = null;
        try {
            socket = new Socket(getHost(), getPort());
            socket.setSoTimeout(READ_SOCKET_TIMEOUT);
            if (logger.isDebugEnabled())
                logger.debug("socket created");
            is = new BufferedReader(new InputStreamReader(socket
                    .getInputStream()));

            String responseLine;
            if (logger.isDebugEnabled()) {
                // read the first two non-alert lines that come from connecting to the server
                logger.debug(is.readLine());
                logger.debug(is.readLine());
            }
            // blocking read waits for data from the server
            while ((responseLine = is.readLine()) != null) {
                if (logger.isDebugEnabled())
                    logger.debug(responseLine);
                if (WXR_PING.equals(responseLine)) {
                     continue;
                }
                try {
                    // parse the data from the server
                    WxrEvent event = new WxrEvent();
                    event.parse(responseLine);
                    // send email
                    emailEvent(event);
                    if (logger.isDebugEnabled())
                        logger.debug(event);
                } catch (InvalidEventException e) {
                    logger.error("Invalid event for '" + responseLine  + "'", e);
                }
            }
        } catch (UnknownHostException e) {
            logger.error(e);
        } catch (SocketException se) {
            logger.debug(se);
        } catch (IOException e) {
            logger.error(e);
        }
        try {
            if (logger.isDebugEnabled())
                logger.debug("closing input stream");
            if (is != null)
                is.close();
        } catch (Exception e) {
            logger.warn(e);
        }
        try {
            if (logger.isDebugEnabled())
                logger.debug("closing socket");
            if (socket != null)
                socket.close();
        } catch (Exception e) {
            logger.warn(e);
        }
    }

    /**
     *  testRun
     *  
     *    simulates a respose from the wxrd server
     *    this can be useful for debugging 
     */
    public void testRun() {
        if (logger.isDebugEnabled())
            logger.debug("run called");
        String responseLine;
        if (logger.isDebugEnabled()) {
            logger.debug("WXRD v0.2 - (C) 2006, bechter.com");
            logger.debug("  All rights reserved.");
        }
        // simulated wxrd response line is a Required Weekly Test for 2 Ohio counties
        responseLine = "WXR RWT 039103-039153 20061130T145709+0600";
        if (logger.isDebugEnabled())
            logger.debug(responseLine);
        try {
            WxrEvent event = new WxrEvent();
            event.parse(responseLine);
            emailEvent(event);
            if (logger.isDebugEnabled())
                logger.debug(event);
        } catch (InvalidEventException e) {
            logger.error("Invalid event for '" + responseLine  + "'", e);
        }
    }

    /**
     * 
     * @return WxrClient gets a singleton instane of the client
     */
    public static WxrClient getInstance() {
        if (instance == null)
            instance = new WxrClient();
        return instance;
    }


    /**
     * emailEvent()
     *   sends email in response to a wxrd message
     *   calls private sendEmail method for each of the SMS and SMTP list of email recipients
     *   
     * @param event a parsed event for which to send email 
     */
    private void emailEvent(WxrEvent event) {
        sendEmail(event, getSmsRecipients(), false);
        sendEmail(event, getEmailRecipients(), true);
    }
    
    /**
     * sendEmail()
     *   sends email for a particular wxrd message to a specified list of recipients
     *   
     * @param event - the parsed wxrd event
     * @param emailRecipients - a List of email addresses to send the message to
     * @param fullText - boolean, if true send full text, if false send abbreviated messare (for SMS)
     */
    private void sendEmail(WxrEvent event, List<String> emailRecipients, boolean fullText) {
        if (logger.isDebugEnabled())
            logger.debug("emailEvent called");
        if (emailRecipients != null && emailRecipients.size() > 0) {
            try {
                Address[] recipients = new Address[emailRecipients.size()];
                for (int i = 0; i < emailRecipients.size(); i++) {
                    recipients[i] = new InternetAddress(emailRecipients.get(i));
                }
                Properties props = new Properties();
                try {
                    props.setProperty("mail.subject", event.getEventTypeDescription());
                } catch (InvalidEventException e) {
                    logger.error(e);
                    props.setProperty("mail.subject", "unknown event (" + event.getEventType() + ") ");
                }
                props.setProperty("mail.from", getMailFrom());
                props.setProperty("mail.to", getMailTo());
                props.setProperty("mail.debug", "false");
                props.setProperty("mail.host", getMailHost());
                props.setProperty("mail.user", getMailUser());
                javax.mail.Session emailSession = javax.mail.Session.getInstance(props);
                MimeMessage msg = new MimeMessage(emailSession);
                msg.setFrom(new InternetAddress(props.getProperty("mail.from")));
                msg.setRecipient(Message.RecipientType.TO, new InternetAddress(
                        props.getProperty("mail.to")));
                msg.setSubject(props.getProperty("mail.subject"));
                msg.setRecipients(RecipientType.BCC, recipients);
                if (fullText)
                    msg.setText(event.toString());
                else {
                    String eventText = null;
                    try {
                        eventText = event.getEventTypeDescription(); 
                    } catch (InvalidEventException iee) {
                        // if the event description lookup fails just send the code
                        eventText = event.getEventType();
                    }
                    msg.setText(eventText + " until " + df.format(event.getExpiresTime()));
                }
		// mark the message "Urgent" 
		msg.addHeader("X-Priority", "1");
		msg.addHeader("Priority", "urgent");
                Transport.send(msg);
            } catch (AddressException e) {
                logger.error(e);
            } catch (MessagingException e) {
                logger.error(e);
            }

        }
    }
    
    /**
     * getHost()
     *   - get the configured wxrd host
     *   
     * @return Returns the host.
     */
    public String getHost() {
        return host;
    }

    /**
     * setHost() 
     *   - sets the wxrd host name parameter
     *   
     * @param host - The host to set.
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * getPort()
     *   - get the configured wxrd port number
     *   
     * @return Returns the port.
     */
    public int getPort() {
        return port;
    }

    /**
     * setPort()
     *   - sets the wxrd port number
     *   
     * @param port - The port to set.
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * @return Returns the emailRecipients.
     */
    public List<String> getEmailRecipients() {
        if (emailRecipients == null) 
            emailRecipients = new ArrayList<String>();
        return emailRecipients;
    }

    /**
     * @param emailRecipients
     *            The emailRecipients to set.
     */
    public void setEmailRecipients(List<String> emailRecipients) {
        this.emailRecipients = emailRecipients;
    }

    /**
     * @param smsRecipients
     *            The smsRecipients to set.
     */
    public void setSmsRecipients(List<String> smsRecipients) {
        this.smsRecipients = smsRecipients;
    }

    /**
     * @return Returns the smsRecipients.
     */
    public List<String> getSmsRecipients() {
        if (smsRecipients == null)
            smsRecipients = new ArrayList<String>();
        return smsRecipients;
    }

    /**
     * @param mailHost
     */
    public void setMailHost(String mailHost) {
        this.mailHost = mailHost;
    }

    /**
     * 
     * @return the SMTP mail host
     */
    public String getMailHost() {
        return mailHost;
    }

    /**
     * setMailUser()
     *   - sets the user login for the SMTP mail host
     *   
     * @param mailUser
     */
    public void setMailUser(String mailUser) {
        this.mailUser = mailUser;
    }

    /**
     * 
     * @return the user login for the SMTP mail host
     */
    public String getMailUser() {
        return mailUser;
    }

    /**
     * sets the email user that the email will be sent to
     * 
     * @param mailTo
     */
    public void setMailTo(String mailTo) {
        this.mailTo = mailTo;
    }

    /**
     *   gets the email user that the email will be sent to
     * @return
     */
    public String getMailTo() {
        return mailTo;
    }

    /**
     *   sets the email address that sent mail will be from
     *   
     * @param mailFrom
     */
    public void setMailFrom(String mailFrom) {
        this.mailFrom = mailFrom;
    }

    /**
     *   gets the email address that sent mail will be from
     * @return
     */
    public String getMailFrom() {
        return mailFrom;
    }
    
    /**
     * sets the test (simulation) mode
     * 
     * @param test - if True, test mode
     */
    public void setTest(boolean test) {
        this.test = test;
    }
    
    /**
     * return true if in test (simulation) mode
     * 
     * @return
     */
    public boolean isTest() {
        return test;
    }

    /**
     * usage()
     * 
     *   displays the usage information 
     * 
     */
    public void usage() {
        System.err.println("WxrClient - listen for weather alert messages from a wxrd server.");
        System.err.println("usage:");
        System.err.println("    -h | --help     : print this message");
        System.err.println("    -t | --host     : hostname of host running wxrd (default is localhost)");
        System.err.println("    -p | --port     : port on host running wxrd to connect to (default is 9317)");
        System.err.println("    -e | --email    : email address (or comma-separated list of addresses) to email");
        System.err.println("    -s | --sms      : sms email gateway address (or comma-separated list of addresses) to sms");
        System.err.println("    -m | --mailhost : SMTP email host to use to send email");
        System.err.println("    -u | --mailuser : SMTP username used to send email");
        System.err.println("    -f | --from     : email address to appear in the \"from\" of the email/sms");
    }

    /**
     * parseAddress()
     *   - splits command line parameters for email addresses
     *     email addresses are not validated against the email spec
     *   
     * @param addresses a list of one or more comma-separated email addresses
     * @param arg - the command line argument co ntaining email address(es)
     */
    private void parseAddresses(List<String> addresses, String arg) {
        StringTokenizer st = new StringTokenizer(arg, ",;");
        while (st.hasMoreTokens())
            addresses.add(st.nextToken());
    }

    /**
     * parseArgs()
     *   - parse the command line arguments
     *   
     * @param args the array of command line argumets
     */
    private void parseArgs(String[] args) {
        int i = 0;
        try {
            while (i < args.length) {
                String arg = args[i];
                if ("-t".equals(arg) || "--host".equals(arg))
                    setHost(args[++i]);
                else if ("-p".equals(arg) || "--port".equals(arg))
                    setPort(Integer.parseInt(args[++i]));
                else if ("-e".equals(arg) || "--email".equals(arg))
                    parseAddresses(getEmailRecipients(), args[++i]);
                else if ("-s".equals(arg) || "--sms".equals(arg))
                    parseAddresses(getSmsRecipients(), args[++i]);
                else if ("-m".equals(arg) || "--mailhost".equals(arg))
                    setMailHost(args[++i]);
                else if ("-u".equals(arg) || "--mailuser".equals(arg))
                    setMailUser(args[++i]);
                else if ("-f".equals(arg) || "--mailfrom".equals(arg))
                    setMailFrom(args[++i]);
                else if ("-h".equals(arg) || "--help".equals(arg)) {
                    usage();
                    System.exit(1);
                } else if ("-T".equals(arg) || "--test".equals(arg))
                    setTest(true);
                else
                    throw new Exception("Invalid argument " + arg);
                i++;
            }
        } catch (Exception e) {
            System.err.println(e.toString());
            usage();
            System.exit(2);
        }
    }

    /**
     * main()
     *   - application entry point
     *   
     * @param args the array of command linme arguments
     */
    public static void main(String[] args) {
        WxrClient wxrClient = getInstance();
        wxrClient.parseArgs(args);
        if (wxrClient.isTest())
            wxrClient.testRun();
        else 
            wxrClient.run();
    }
}

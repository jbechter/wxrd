/*
   SimpleClient.java

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
*/

package com.bechter.wxrd.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;


public class SimpleClient {
    static final String host = "localhost";
    static final int port = 9317;

    public static void main(String[] args) {
        BufferedReader is = null; 
        Socket s = null; 
        try {
            // open a connection to the server on port 9317
            s = new Socket(host, port);
            is = new BufferedReader(new InputStreamReader(
                    s.getInputStream()));
            String responseLine;
            // wait for the server to send data
            while ((responseLine = is.readLine()) != null) {
                // server has sent a message, print it
                System.out.println("Server: " + responseLine);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}


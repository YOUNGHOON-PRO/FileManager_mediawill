package com.enders.controller;

import com.enders.client.realtime.RealTimeExternalAgent;
import com.enders.client.realtime.RealTimeInternalAgent;
import com.enders.server.realtime.RealTimeExtenalServer;
import com.enders.server.realtime.RealTimeIntenalServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RealTimeController extends Thread {
	
	private static final Logger LOGGER = LogManager.getLogger(RealTimeController.class.getName());
	
  public int TARGET = 0;
  
  public static final int MODULE_EXT = 1;
  
  public static final int MODULE_INT = 2;
  
  public RealTimeController(int module) {
    this.TARGET = module;
  }
  
  public void run() {
    try {
      if (this.TARGET == 1) {
        RealTimeExtenalServer server = new RealTimeExtenalServer();
        server.start();
        LOGGER.info("EXT Server run ");
        //System.out.println("EXT Server run ");
        while (true) {
          RealTimeExternalAgent agent = new RealTimeExternalAgent();
          agent.start();
          LOGGER.info("EXT Agent run ");
          //System.out.println("EXT Agent run ");
          Thread.sleep(10000L);
        } 
      } 
      if (this.TARGET == 2) {
        RealTimeIntenalServer server = new RealTimeIntenalServer();
        server.start();
        LOGGER.info("INT Server run ");
        //System.out.println("INT Server run ");
        while (true) {
          RealTimeInternalAgent agent = new RealTimeInternalAgent();
          agent.start();
          LOGGER.info("INT Agent run ");
          //System.out.println("INT Agent run ");
          Thread.sleep(10000L);
        } 
      } 
    } catch (Exception e) {
    	LOGGER.error(e);
    	//e.printStackTrace();
    } 
  }
  
  public static void main(String[] ar) throws Exception {
    if (ar.length == 0) {
    	LOGGER.info(">>>> EXT start");
    	//System.out.println(">>>> EXT start");
      RealTimeController rc_ext = new RealTimeController(1);
      rc_ext.start();
      LOGGER.info(">>>> INT start");
      //System.out.println(">>>> INT start");
      RealTimeController rc_int = new RealTimeController(2);
      rc_int.start();
    } else if (ar[0].equals("EXT")) {
    	LOGGER.info(">>>> EXT start");
    	//System.out.println(">>>> EXT start");
      RealTimeController rc_ext = new RealTimeController(1);
      rc_ext.start();
    } else if (ar[0].equals("INT")) {
    	LOGGER.info(">>>> INT start");
    	//System.out.println(">>>> INT start");
      RealTimeController rc_int = new RealTimeController(2);
      rc_int.start();
    } 
  }
}

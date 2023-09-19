package com.enders.controller;

import com.enders.client.bulk.BulkInternalReceiveAgent;
import com.enders.client.bulk.BulkInternalSendAgent;
import com.enders.client.realtime.RealTimeExternalAgent;
import com.enders.client.realtime.RealTimeInternalAgent;
import com.enders.server.bulk.BulkExtenalServer;
import com.enders.server.realtime.RealTimeExtenalServer;
import com.enders.server.realtime.RealTimeIntenalServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BulkController extends Thread {
	
	private static final Logger LOGGER = LogManager.getLogger(BulkController.class.getName());
	
  public int TARGET = 0;
  
  public static final int MODULE_REALTIME_EXT = 1;
  
  public static final int MODULE_REALTIME_INT = 2;
  
  public static final int MODULE_BULK_EXT = 3;
  
  public static final int MODULE_BULK_INT_SEND = 4;
  
  public static final int MODULE_BULK_INT_RECEIVE = 5;
  
  public String taskId = "";
  
  public BulkController(int module) {
    this.TARGET = module;
  }
  
  public BulkController(int module, String taskId) {
    this(module);
    this.taskId = taskId;
  }
  
  public void run() {
    try {
      if (this.TARGET == 1) {
        RealTimeExtenalServer server = new RealTimeExtenalServer();
        server.start();
        LOGGER.info("Realtime External Server run ");
        //System.out.println("Realtime External Server run ");
        while (true) {
          RealTimeExternalAgent agent = new RealTimeExternalAgent();
          agent.start();
          LOGGER.info("Realtime External Agent run ");
          //System.out.println("Realtime External Agent run ");
          Thread.sleep(10000L);
        } 
      } 
      if (this.TARGET == 2) {
        RealTimeIntenalServer server = new RealTimeIntenalServer();
        server.start();
        LOGGER.info("Realtime Internal Server run ");
        //System.out.println("Realtime Internal Server run ");
        while (true) {
          RealTimeInternalAgent agent = new RealTimeInternalAgent();
          agent.start();
          LOGGER.info("Realtime Internal Agent run ");
          //System.out.println("Realtime Internal Agent run ");
          Thread.sleep(10000L);
        } 
      } 
      if (this.TARGET == 3) {
        BulkExtenalServer server = new BulkExtenalServer();
        server.start();
        LOGGER.info("BULK External Server run ");
        //System.out.println("BULK External Server run ");
      } else if (this.TARGET == 4) {
    	  LOGGER.info("BULK External Send Agent run");
    	  //System.out.println("BULK External Send Agent run");
        BulkInternalSendAgent agent = new BulkInternalSendAgent(this.taskId);
        boolean state = agent.run();
        LOGGER.info("MESSAGE : " + state);
        //System.out.println("MESSAGE : " + state);
      } else if (this.TARGET == 5) {
    	  LOGGER.info("BULK External Receive Agent run");
          //System.out.println("BULK External Receive Agent run");
        BulkInternalReceiveAgent agent = new BulkInternalReceiveAgent();
        agent.run();
      } 
    } catch (Exception e) {
    	LOGGER.error(e);
        //e.printStackTrace();
    } 
  }
  
  public static void main(String[] ar) throws Exception {
    if (ar.length == 0) {
    	LOGGER.info(">>>> REALTIME_EXT start");
        //System.out.println(">>>> REALTIME_EXT start");
      BulkController rc_ext = new BulkController(1);
      rc_ext.start();
      LOGGER.info(">>>> REALTIME_INT start");
      //System.out.println(">>>> REALTIME_INT start");
      BulkController rc_int = new BulkController(2);
      rc_int.start();
    } else if (ar[0].equals("REALTIME_EXT")) {
    	LOGGER.info(">>>> REALTIME_EXT start");
    	//System.out.println(">>>> REALTIME_EXT start");
      BulkController rc_ext = new BulkController(1);
      rc_ext.start();
    } else if (ar[0].equals("REALTIME_INT")) {
    	LOGGER.info(">>>> REALTIME_INT start");
    	//System.out.println(">>>> REALTIME_INT start");
      BulkController rc_int = new BulkController(2);
      rc_int.start();
    } else if (ar[0].equals("BULK_EXT")) {
    	LOGGER.info(">>>> BULK_EXT start");
        //System.out.println(">>>> BULK_EXT start");
      BulkController rc_int = new BulkController(3);
      rc_int.start();
    } else if (ar[0].equals("BULK_INT_SEND")) {
    	LOGGER.info(">>>> BULK_INT_SEND start");
        //System.out.println(">>>> BULK_INT_SEND start");
      BulkController rc_int = new BulkController(4);
      rc_int.start();
    } else if (ar[0].equals("BULK_INT_RECEIVE")) {
    	LOGGER.info(">>>> BULK_INT_RECEIVE start");
    	//System.out.println(">>>> BULK_INT_RECEIVE start");
      BulkController rc_int = new BulkController(5);
      rc_int.start();
    } 
  }
}

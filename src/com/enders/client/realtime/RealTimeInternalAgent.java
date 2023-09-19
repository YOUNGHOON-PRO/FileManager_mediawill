package com.enders.client.realtime;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.Socket;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RealTimeInternalAgent extends Thread{
	
	private static final Logger LOGGER = LogManager.getLogger(RealTimeInternalAgent.class.getName());
	
	public int CONNECT_PORT;
	public String CONNECT_HOST;
	public String RECEIVE_PATH;
	public String LOG_FILE_PATH;
	public PrintWriter logger;
	public static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	public void logWrite(String message) {
	    if (this.logger == null) {
	      boolean fileAppend = false;
	      boolean autoFlush = true;
	      try {
	        this.logger = new PrintWriter(new FileWriter(new File(this.LOG_FILE_PATH), fileAppend), autoFlush);
	      } catch (Exception e) {
	    	  LOGGER.error(e);
	          //e.printStackTrace();
	      } 
	    } 
	    LOGGER.info(message);
	    //this.logger.println(message);
	  }
	
	@SuppressWarnings("static-access")
	public void loadProperties() {
	    FileInputStream fileInput = null;
	    Properties props = new Properties();
	    try {
	      if (System.getProperty("hasprop") == null || Boolean.valueOf(System.getProperty("hasprop")).booleanValue()) {
	        props.load(getClass().getClassLoader().getResource("RealTimeInternal.properties").openStream());
	      } else {
	        fileInput = new FileInputStream(Paths.get(System.getProperty("proppath"), new String[0]).toFile());
	        props.load(fileInput);
	      } 
	      this.RECEIVE_PATH = props.getProperty("RECEIVE.BASE_DIR");
	      this.LOG_FILE_PATH = props.getProperty("RECEIVE.LOG_FILE_PATH");
	      this.CONNECT_HOST = props.getProperty("RECEIVE.CONNECT_HOST");
	      this.CONNECT_PORT = Integer.parseInt(props.getProperty("RECEIVE.CONNECT_PORT"));
	    } catch (Exception e) {
	    	LOGGER.error(e);
	        //e.printStackTrace();
	    } finally {
	      if (fileInput != null)
	        try {
	          fileInput.close();
	        } catch (IOException e) {
	        	LOGGER.error(e);
	            //e.printStackTrace();
	        }  
	    } 
	  }
	
	public void run() {
	    loadProperties();
	    while (true) {
	      try {
	    	  LOGGER.info(String.format("CONNECT HOST/PORT : %s / %s", new Object[] { this.CONNECT_HOST, Integer.valueOf(this.CONNECT_PORT) }));
	    	  //System.out.println(String.format("CONNECT HOST/PORT : %s / %s", new Object[] { this.CONNECT_HOST, Integer.valueOf(this.CONNECT_PORT) }));
	        Socket s = new Socket(this.CONNECT_HOST, this.CONNECT_PORT);
	        receivce(s.getInputStream());
	        s.close();
	      } catch (IOException e) {
	    	  LOGGER.error(e);
	          //System.out.println("IOException Error ::: " + e.getMessage());
	        continue;
	      } catch (Exception e) {
	    	  LOGGER.error(e);
 	          //System.out.println("Exception Error ::: " + e.getMessage());
	        continue;
	      } finally {
	        try {
	          Thread.sleep(10000L);
	        } catch (InterruptedException e) {
	        	LOGGER.error(e);
	        	//e.printStackTrace();
	        } 
	      } 
	    } 
	  }

	public void receivce(InputStream is) throws Exception {
	    PrintWriter pw = null;
	    BufferedReader br = null;
	    String filePath = null;
	    Properties p = null;
	    StringReader sr = null;
	    String lineString = null;
	    List<String> completedList = new ArrayList<>();
	    try {
	      br = new BufferedReader(new InputStreamReader(is));
	      while ((lineString = br.readLine()) != null) {
	        if (lineString.indexOf("FILE_PATH=") > -1) {
	          if (pw != null)
	            pw.close(); 
	          String propString = lineString;
	          p = new Properties();
	          sr = new StringReader(propString);
	          p.load(sr);
	          filePath = makeDirectory(p.getProperty("FILE_PATH"));
	          pw = new PrintWriter(new FileWriter(new File(filePath)), true);
	          completedList.add(lineString);
	          continue;
	        } 
	        pw.println(lineString.trim());
	      } 
	    } catch (Exception e) {
	    	LOGGER.error(e);
	        //e.printStackTrace();
	    } finally {
	      if (pw != null) {
	        pw.flush();
	        pw.close();
	      } 
	      postTrigger(completedList);
	    } 
	  }
	
	public String makeDirectory(String str) {
	    String base = this.RECEIVE_PATH;
	    StringTokenizer st = new StringTokenizer(str.replace(base, ""), "/");
	    String path = this.RECEIVE_PATH;
	    String next = "";
	    while (st.hasMoreTokens()) {
	      next = st.nextToken();
	      path = String.valueOf(path) + "/" + next;
	      File f = new File(path);
	      if (!f.exists() && st.hasMoreTokens())
	        f.mkdir(); 
	    } 
	    return path;
	  }
	
	
	public void postTrigger(List<String> completedList) {
	    for (String key : completedList) {
	      // 1. ���� ���� �α� ��� 	
	      logWrite(String.format("Receive Time : %s, %s", new Object[] { sdf.format(Calendar.getInstance().getTime()), key }));
	    } 
	  }
	  
	  public static void main(String[] ar) throws Exception {
	    RealTimeInternalAgent agent = new RealTimeInternalAgent();
	    agent.start();
	  }
}

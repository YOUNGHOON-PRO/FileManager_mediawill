package com.enders.client.realtime;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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

public class RealTimeExternalAgent extends Thread{
	
	private static final Logger LOGGER = LogManager.getLogger(RealTimeExternalAgent.class.getName());
	
	public int CONNECT_PORT;
	public String CONNECT_HOST;
	public String RECEIVE_PATH;
	public String LOG_FILE_PATH;
	public String CLEAN_MERGE_PATH;
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
	        props.load(getClass().getClassLoader().getResource("RealTimeExternal.properties").openStream());
	      } else {
	        fileInput = new FileInputStream(Paths.get(System.getProperty("proppath"), new String[0]).toFile());
	        props.load(fileInput);
	      } 
	      this.RECEIVE_PATH = props.getProperty("RECEIVE.BASE_DIR");
	      this.LOG_FILE_PATH = props.getProperty("RECEIVE.LOG_FILE_PATH");
	      this.CONNECT_HOST = props.getProperty("RECEIVE.CONNECT_HOST");
	      this.CONNECT_PORT = Integer.parseInt(props.getProperty("RECEIVE.CONNECT_PORT"));
	      this.CLEAN_MERGE_PATH = props.getProperty("RECEIVE.CLEAN_MERGE_PATH");
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
	        receiveBinary(s);
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

	
	public void receiveBinary(Socket socket) throws Exception {
	    BufferedReader br = null;
	    String filePath = null;
	    Properties p = null;
	    StringReader sr = null;
	    String lineString = null;
	    List<String> completedList = new ArrayList<>();
	    File zipFile = null;
	    String processTask = "";
	    byte[] mybytearray = null;
	    int fileLen = 0;
	    PrintWriter sos = new PrintWriter(socket.getOutputStream());
	    BufferedOutputStream bos = null;
	    try {
	      br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
	      while ((lineString = br.readLine()) != null) {
	        processTask = "Task : File Path";
	        if (lineString.indexOf("FILE_PATH=") > -1) {
	          String propString = lineString;
	          p = new Properties();
	          sr = new StringReader(propString.replace("\\", "\\\\"));
	          p.load(sr);
	          filePath = makeDirectory(p.getProperty("FILE_PATH"));
	          LOGGER.info("file path = " + filePath);
	          //System.out.println("file path = " + filePath);
	          zipFile = new File(filePath);
	          bos = new BufferedOutputStream(new FileOutputStream(zipFile));
	          completedList.add(lineString);
	          sos.println("OK");
	        } else {
	          sos.println(String.format("Fail : %s", new Object[] { processTask }));
	        } 
	        sos.flush();
	        processTask = "Task : File Size";
	        lineString = br.readLine();
	        if (lineString.indexOf("FILE_SIZE=") > -1) {
	          String propString = lineString;
	          p = new Properties();
	          sr = new StringReader(propString.replace("\\", "\\\\"));
	          p.load(sr);
	          fileLen = Integer.parseInt(p.getProperty("FILE_SIZE"));
	          LOGGER.info("File Size = " + fileLen);
	          //System.out.println("File Size = " + fileLen);
	          sos.println("OK");
	        } else {
	          sos.println(String.format("Fail : %s", new Object[] { processTask }));
	        } 
	        sos.flush();
	        processTask = "Task : File Download";
	        mybytearray = new byte[fileLen];
	        int current = 0;
	        InputStream is = socket.getInputStream();
	        int readBytes;
	        while ((readBytes = is.read(mybytearray, current, mybytearray.length - current)) > 0)
	          current += readBytes; 
	        bos.write(mybytearray, 0, current);
	        bos.flush();
	        bos.close();
	        sos.println("FINISH");
	        sos.flush();
	      } 
	    } catch (Exception e) {
	      LOGGER.error(e);
	      //Exception exception1 = null;
	      //sos.println(String.format("Fail : %s", new Object[] { processTask }));
	      //exception1.printStackTrace();
	    } finally {
	      if (bos != null) {
	        bos.flush();
	        bos.close();
	      } 
	      postTrigger(completedList);
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
	          String propString = String.valueOf(lineString) + "\r" + br.readLine();
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
	    File base = new File(String.valueOf(this.RECEIVE_PATH) + this.CLEAN_MERGE_PATH);
	    File[] folderList = base.listFiles();
	    if (folderList.length > 0) {
	      byte b;
	      int i;
	      File[] arrayOfFile;
	      for (i = (arrayOfFile = folderList).length, b = 0; b < i; ) {
	        File f = arrayOfFile[b];
	        if (f.isDirectory())
	          f.delete(); 
	        b++;
	      } 
	    } 
	  }
	  
	  public static void main(String[] ar) throws Exception {
	    RealTimeExternalAgent agent = new RealTimeExternalAgent();
	    agent.start();
	  }
}

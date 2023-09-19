package com.enders.client.bulk;

import com.enders.utils.CompressZip;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Properties;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BulkInternalSendAgent {
	
	private static final Logger LOGGER = LogManager.getLogger(BulkInternalSendAgent.class.getName());
	
	public String BASE_DIR;
	public String TASK_ID;
	public int CONNECT_PORT;
	public String CONNECT_HOST;
	public String RECEIVE_PATH;
	public String ZIP_FILE_PATH;
	public boolean ZIP_REMOVE;
	public boolean ORIGIN_FILE_REMOVE;
	public File ZIP_FILE;
	public String LOG_FILE_PATH;
	public PrintWriter logger;
	public static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	public PrintWriter bw;
	public BufferedOutputStream bos;
	public boolean status = false;
	
	public BulkInternalSendAgent(String taskId) {
	    this.TASK_ID = taskId;
	  }
	  
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
	    try {
	      Properties props = new Properties();
	      if (System.getProperty("hasprop") == null || Boolean.valueOf(System.getProperty("hasprop")).booleanValue()) {
	        props.load(getClass().getClassLoader().getResource("BulkInternal.properties").openStream());
	      } else {
	        props.load(new FileInputStream(Paths.get(System.getProperty("proppath"), new String[0]).toFile()));
	      } 
	      this.BASE_DIR = props.getProperty("SEND.BASE_DIR");
	      this.ZIP_FILE_PATH = props.getProperty("SEND.ZIP_FILE_PATH");
	      this.LOG_FILE_PATH = props.getProperty("SEND.LOG_FILE_PATH");
	      this.CONNECT_HOST = props.getProperty("SEND.CONNECT_HOST");
	      this.ZIP_REMOVE = Boolean.valueOf(props.getProperty("SEND.ZIP_REMOVE")).booleanValue();
	      this.ORIGIN_FILE_REMOVE = Boolean.valueOf(props.getProperty("SEND.ORIGIN_FILE_REMOVE")).booleanValue();
	      this.CONNECT_PORT = Integer.parseInt(props.getProperty("SEND.CONNECT_PORT"));
	    } catch (Exception e) {
	      LOGGER.error(e);
	      //e.printStackTrace();
	    } 
	  }
	
	public void sendOperator(PrintWriter out) {}
	

	private void lookup() {
	    try {
	      CompressZip zip = new CompressZip();
	      Stream<Path> stream = zip.findTaskFile(this.BASE_DIR, this.TASK_ID, "");
	      zip.taskZip(this.TASK_ID, stream, this.ZIP_FILE_PATH, "transfer", this.ORIGIN_FILE_REMOVE);
	      this.ZIP_FILE = zip.getZipFile();
	    } catch (Exception e) {
	      LOGGER.error(e);
	      //e.printStackTrace();
	    } 
	  }

	public void sendBinary(OutputStream os, InputStream is) throws Exception {
	    FileInputStream fis = null;
	    PrintWriter pw = null;
	    BufferedReader brs = null;
	    String accept = null;
	    long fileSize = 0L;
	    try {
	      pw = new PrintWriter(os);
	      brs = new BufferedReader(new InputStreamReader(is));
	      pw.println("PUT");
	      pw.flush();
	      LOGGER.info("Server Response Waiting...");
	      //System.out.println("Server Response Waiting...");
	      accept = brs.readLine();
	      if (accept.equals("OK")) {
	    	  LOGGER.info("Response : OK");
	    	  //System.out.println("Response : OK");
	      } else {
	        throw new Exception(String.format("Response : BAD Response, detail : %s", new Object[] { accept }));
	      } 
	      pw.println("FILE_PATH=" + this.ZIP_FILE.getAbsolutePath());
	      pw.flush();
	      LOGGER.info("FILE_PATH=" + this.ZIP_FILE.getAbsolutePath());
	      //System.out.println("FILE_PATH=" + this.ZIP_FILE.getAbsolutePath());
	      accept = brs.readLine();
	      if (accept.equals("OK")) {
	    	  LOGGER.info("FILE_PATH=" + this.ZIP_FILE.getAbsolutePath());
	          //System.out.println("FILE_PATH=" + this.ZIP_FILE.getAbsolutePath());
	      } else {
	        throw new Exception(String.format("Response : BAD Response, detail : %s", new Object[] { accept }));
	      } 
	      fileSize = this.ZIP_FILE.length();
	      pw.println("FILE_SIZE=" + fileSize);
	      pw.flush();
	      LOGGER.info("FILE_PATH=" + fileSize);
	      //System.out.println("FILE_PATH=" + fileSize);
	      accept = brs.readLine();
	      if (accept.equals("OK")) {
	    	  LOGGER.info("Response : OK");
	    	  //System.out.println("Response : OK");
	      } else {
	        throw new Exception(String.format("Response : BAD Response, detail : %s", new Object[] { accept }));
	      } 
	      fis = new FileInputStream(this.ZIP_FILE);
	      byte[] buf = new byte[1024];
	      int read = 0;
	      long sendSize = 0L;
	      LOGGER.info("File Send Start.");
	      //System.out.println("File Send Start.");
	      while ((read = fis.read(buf, 0, buf.length)) != -1) {
	        sendSize += read;
	        os.write(buf, 0, read);
	        os.flush();
	      } 
	      LOGGER.info("File Send Completed.");
	      //System.out.println("File Send Completed.");
	      accept = brs.readLine();
	      if (accept.equals("FINISH")) {
	    	LOGGER.info("Response : Finished");
	        //System.out.println("Response : Finished");
	      } else {
	        throw new Exception(String.format("Response : BAD Response, detail : %s", new Object[] { accept }));
	      } 
	      this.status = true;
	    } catch (Exception e) {
	      LOGGER.error(e);
	      //e.printStackTrace();
	    } finally {
	      if (fis != null)
	        fis.close(); 
	    } 
	    postTrigger();
	  }

	public void sendString(OutputStream os, InputStream is) throws Exception {
		// TODO Auto-generated method stub
		PrintWriter pw = null;
		BufferedReader brs = null;
		try {
			pw = new PrintWriter(os);
			pw.println("PUT");
			pw.flush();
			
			brs = new BufferedReader(new InputStreamReader(is));
			LOGGER.info("Server Response Waiting...");
			//System.out.println("Server Response Waiting...");
			String accept = brs.readLine();
			
			if (accept.equals("OK")) { 
				LOGGER.info("Response : OK");
				//System.out.println("Response : OK");
			}else { 
				throw new Exception("Response : BAD Response");
			}
			
			LOGGER.info("SEND ZIP File :: " + this.ZIP_FILE.getAbsolutePath());
			//System.out.println("SEND ZIP File :: " + this.ZIP_FILE.getAbsolutePath());
			pw.println("FILE_PATH=" + this.ZIP_FILE.getAbsolutePath());
			
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(this.ZIP_FILE)));
			String str = new String();
			while ((str = br.readLine()) != null) {
				pw.println(str);
			}
			
			pw.flush();
			br.close();
			
		} catch (Exception e) {
			LOGGER.error(e);
			//e.printStackTrace();
		} finally {
			 pw.close();
		}
		
		postTrigger();
		
		LOGGER.info("Successful End Task");
		//System.out.println("Successful End Task");
	}
	
	public boolean run() {
	    boolean returnValue = false;
	    Socket s = null;
	    try {
	      loadProperties();
	      lookup();
	      LOGGER.info(String.format("CONNECT HOST/PORT : %s / %s, Task Id : %s", new Object[] { this.CONNECT_HOST, Integer.valueOf(this.CONNECT_PORT), this.TASK_ID }));
	      //System.out.println(String.format("CONNECT HOST/PORT : %s / %s, Task Id : %s", new Object[] { this.CONNECT_HOST, Integer.valueOf(this.CONNECT_PORT), this.TASK_ID }));
	      s = new Socket(this.CONNECT_HOST, this.CONNECT_PORT);
	      sendOperator(this.bw);
	      sendBinary(s.getOutputStream(), s.getInputStream());
	      returnValue = this.status;
	    } catch (IOException e) {
	      LOGGER.error(e);
	      //System.out.println("IOException Error ::: " + e.getMessage());
	    } catch (Exception e) {
	      LOGGER.error(e);
	      //System.out.println("Exception Error ::: " + e.getMessage());
	    } finally {
	      try {
	        if (s != null)
	          s.close(); 
	      } catch (Exception ee) {
	    	LOGGER.error(ee);
	        //ee.printStackTrace();
	      } 
	    } 
	    return returnValue;
	  }
	  
	  public void postTrigger() {
		LOGGER.info(String.format("Receive Time : %s, %s", new Object[] { sdf.format(Calendar.getInstance().getTime()), this.ZIP_FILE.getName() }));
	    //logWrite(String.format("Receive Time : %s, %s", new Object[] { sdf.format(Calendar.getInstance().getTime()), this.ZIP_FILE.getName() }));
	    if (this.status && this.ZIP_REMOVE)
	      this.ZIP_FILE.delete(); 
	  }
	  
	  public static void main(String[] ar) throws Exception {
	    BulkInternalSendAgent agent = new BulkInternalSendAgent("1251-1^1");
	    boolean state = agent.run();
	    LOGGER.info("state ==> " + state);
	    //System.out.println("state ==> " + state);
	  }
}

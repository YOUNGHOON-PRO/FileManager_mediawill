package com.enders.server.realtime;

import com.enders.inf.BaseServer;
import com.enders.inf.DirectoryLookup;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.commons.io.FileUtils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/*
 * 실시간 UMS 내외부간 파일 전송 매니저(내부 전용)
 * 1. Queue_0\LOG 하위 디렉터리를 Hooking 하고
 * 2. Socket 요청 시에 Socket Stream 을 통해 파일을 전송
 * 3. Post Trigger (파일 삭제, Log 기록) 수행  
 */
public class RealTimeIntenalServer extends BaseServer{
	
	private static final Logger LOGGER = LogManager.getLogger(RealTimeIntenalServer.class.getName());
	
	public int PORT = 0;
	public String BASE_DIR;
	public static String LOG_FILE_PATH;
	public static PrintWriter logger;
	public static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	public ServerSocket ss;
	
	public static void logWrite(String message) {
	    if (logger == null) {
	      boolean fileAppend = false;
	      boolean autoFlush = true;
	      try {
	        logger = new PrintWriter(new FileWriter(new File(LOG_FILE_PATH), fileAppend), autoFlush);
	      } catch (Exception e) {
	    	  LOGGER.error(e);
	    	  //e.printStackTrace();
	      } 
	    } 
	    LOGGER.info(message);
	    //logger.println(message);
	  }
	
	@SuppressWarnings("static-access")
	public void loadProperties() {
	    try {
	      Properties props = new Properties();
	      if (System.getProperty("hasprop") == null || Boolean.valueOf(System.getProperty("hasprop")).booleanValue()) {
	        props.load(getClass().getClassLoader().getResource("RealTimeInternal.properties").openStream());
	      } else {
	        props.load(new FileInputStream(Paths.get(System.getProperty("proppath"), new String[0]).toFile()));
	      } 
	      this.BASE_DIR = props.getProperty("SERVER.BASE_DIR");
	      this.PORT = Integer.parseInt(props.getProperty("SERVER.PORT"));
	      LOG_FILE_PATH = props.getProperty("SERVER.LOG_FILE_PATH");
	    } catch (Exception e) {
	    	LOGGER.error(e);
	    	//e.printStackTrace();
	    } 
	  }

	public RealTimeIntenalServer() {
	    loadProperties();
	    LOGGER.info("::: RealTimeIntenalServer Server Started :::");
	    LOGGER.info("::: Listening Time : %s, Service Port %s::: ", new Object[] { sdf.format(Calendar.getInstance().getTime()), Integer.valueOf(this.PORT) });
	    //System.out.println("::: RealTimeIntenalServer Server Started :::");
	    //System.out.println(String.format("::: Listening Time : %s, Service Port %s::: ", new Object[] { sdf.format(Calendar.getInstance().getTime()), Integer.valueOf(this.PORT) }));
	    try {
	      this.ss = new ServerSocket(this.PORT);
	    } catch (Exception e) {
	    	LOGGER.error(e);
	    	//e.printStackTrace();
	    } 
	  }
	
	public void run() {
	    long receiveCount = 0L;
	    try {
	      while (true) {
	        Socket so = this.ss.accept();
	        //logWrite(String.format("%s Agent Connect Count : %d, ", new Object[] { sdf.format(Calendar.getInstance().getTime()), Long.valueOf(receiveCount) }));
	        logWrite(String.format("%s Agent Connect", new Object[] { sdf.format(Calendar.getInstance().getTime())}));
	        DirectoryLookup dl = new Process(this.BASE_DIR, so);
	        dl.start();
	        receiveCount++;
	      } 
	    } catch (Exception e) {
	    	LOGGER.error(e);
	    	//e.printStackTrace();
	      return;
	    } 
	  }
	
	class Process extends DirectoryLookup{
		String root = "";
		Map<String, File> map;
		Socket socket;
		
		public Process(String root, Socket s) {
			super(root);
			this.map = new HashMap<>();
		    this.socket = s;
		}
		
		public void run() {
		      try {
		        lookup();
		        sendBinary(this.socket.getOutputStream(), this.socket.getInputStream());
		      } catch (Exception e) {
		    	  LOGGER.error(e);
		    	  //e.printStackTrace();
		      } finally {
		        try {
		          this.socket.close();
		        } catch (Exception ee) {
		        	LOGGER.error(ee);
		        	//ee.printStackTrace();
		        } 
		      } 
		    }

		/**
		 * SendLog 폴더의 파일들을 조사해서 Queue 에 보관한다.
		 */
		public void lookup() {
			File f = new File(super.BASE_DIR);
			File[] files = f.listFiles();

			/* Base : /Queue_0/Merge_Queue
			 * Target :  /TaskId_0/1/*.eml
			 */
			for (File file : files) {
				if( file.isDirectory() ) { 
					String taskId = file.getName();
					File[] taskList = file.listFiles();
					for(File subTask : taskList) {
						File[] subTaskList = subTask.listFiles(); 
						for(File emlFile : subTaskList) { 
							map.put(taskId, emlFile);
						}
					}
				} 
			}
		}

		/**
		 * 연결된 OutputStream 을 통해 File 을 전송한다.
		 */
		public void sendBinary(OutputStream os, InputStream is) throws Exception {
		      FileInputStream fis = null;
		      PrintWriter pw = null;
		      BufferedReader brs = null;
		      String accept = null;
		      pw = new PrintWriter(os);
		      brs = new BufferedReader(new InputStreamReader(is));
		      List<String> completedList = new ArrayList<>();
		      Set<String> keys = this.map.keySet();
		      for (String key : keys) {
		        try {
		          File file = this.map.get(key);
		          pw.println("FILE_PATH=" + file.getAbsolutePath());
		          pw.flush();
		          LOGGER.info("FILE_PATH=" + file.getAbsolutePath());
		          //System.out.println("FILE_PATH=" + file.getAbsolutePath());
		          accept = brs.readLine();
		          if (accept.equals("OK")) {
		        	  LOGGER.info("Response : OK");
		        	  //System.out.println("Response : OK");
		          } else {
		            throw new Exception(String.format("Response : BAD Response, detail : %s", new Object[] { accept }));
		          } 
		          pw.println("FILE_SIZE=" + file.length());
		          pw.flush();
		          LOGGER.info("FILE_PATH=" + file.length());
		          //System.out.println("FILE_PATH=" + file.length());
		          accept = brs.readLine();
		          if (accept.equals("OK")) {
		        	  LOGGER.info("Response : OK");
		        	  //System.out.println("Response : OK");
		          } else {
		            throw new Exception(String.format("Response : BAD Response, detail : %s", new Object[] { accept }));
		          } 
		          byte[] bytelen = new byte[(int)file.length()];
		          fis = new FileInputStream(file);
		          fis.read(bytelen, 0, bytelen.length);
		          os.write(bytelen, 0, bytelen.length);
		          os.flush();
		          accept = brs.readLine();
		          if (accept.equals("FINISH")) {
		        	  LOGGER.info("Response : Finished");
		        	  //System.out.println("Response : Finished");
		            completedList.add(key);
		          } else {
		            throw new Exception(String.format("Response : BAD Response, detail : %s", new Object[] { accept }));
		          } 
		        } catch (Exception e) {
		        	LOGGER.error(e);
		        	//e.printStackTrace();
		          continue;
		        } finally {
		          fis.close();
		        } 
		      } 
		      os.close();
		      postTrigger(completedList);
		      LOGGER.info("Successful End Task");
		      //System.out.println("Successful End Task");
		    }
		
		public void postTrigger(List<String> completedList) {
			/* TODO
			 * 1. 전송 파일 로그 기록
			 * 2. 디렉토리 삭제 + 파일 삭제
			 * 3. Queue 제거   
			 */
			
			completedList.forEach( (key) -> {
				File f = new File(BASE_DIR + "/" + key);
				// 1. 전송 파일 로그 기록 
				RealTimeIntenalServer.logWrite(String.format("%s Task Id : %s", sdf.format(Calendar.getInstance().getTime()), key));
				
				// 2. 디렉토리 + 파일 삭제 
				try {
					FileUtils.cleanDirectory(f);
					f.delete();
				} catch (IOException e) {
					LOGGER.error(e);
					//e.printStackTrace();
				}
				
				// 3. 큐 제거 
				this.map.remove(key);
			});
			
		}

		@Override
		public void receiveBinary(Socket socket) throws Exception {}
		@Override
		public void sendBinary(OutputStream os) throws Exception {}
	}
	
	 public static void main(String[] ar) throws Exception {
		    RealTimeIntenalServer server = new RealTimeIntenalServer();
		    server.start();
		    LOGGER.info("INT Server run ");
		    //System.out.println("INT Server run ");
		  }
	
}

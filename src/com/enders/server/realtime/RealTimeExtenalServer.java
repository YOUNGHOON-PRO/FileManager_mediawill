package com.enders.server.realtime;

import com.enders.inf.BaseServer;
import com.enders.inf.DirectoryLookup;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/*
 * 실시간 UMS 내외부간 파일 전송 매니저(내부 전용)
 * 1. Queue_0\LOG 하위 디렉터리를 Hooking 하고
 * 2. Socket 요청 시에 Socket Stream 을 통해 파일을 전송
 * 3. Post Trigger (파일 삭제, Log 기록) 수행  
 */
public class RealTimeExtenalServer extends BaseServer{
	
	private static final Logger LOGGER = LogManager.getLogger(RealTimeExtenalServer.class.getName());
	
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
	        props.load(getClass().getClassLoader().getResource("RealTimeExternal.properties").openStream());
	      } else {
	        props.load(new FileInputStream(Paths.get(System.getProperty("proppath"), new String[0]).toFile()));
	      } 
	      this.BASE_DIR = props.getProperty("SERVER.BASE_DIR");
	      LOG_FILE_PATH = props.getProperty("SERVER.LOG_FILE_PATH");
	      this.PORT = Integer.parseInt(props.getProperty("SERVER.PORT"));
	    } catch (Exception e) {
	    	LOGGER.error(e);
	    	//e.printStackTrace();
	    } 
	  }

	public RealTimeExtenalServer() {
	    loadProperties();
	    LOGGER.info("::: RealTimeExtenalServer Server Started :::");
	    LOGGER.info(String.format("::: Listening Time : %s, Service Port %s::: ", new Object[] { sdf.format(Calendar.getInstance().getTime()), Integer.valueOf(this.PORT) }));
	    //System.out.println("::: RealTimeExtenalServer Server Started :::");
	    //System.out.println(String.format("::: Listening Time : %s, Service Port %s::: ", new Object[] { sdf.format(Calendar.getInstance().getTime()), Integer.valueOf(this.PORT) }));
	    try {
	      this.ss = new ServerSocket(this.PORT);
	    } catch (Exception e) {
	    	LOGGER.error(e);
	    	//e.printStackTrace();
	    } 
	  }
	
	public void run () { 
		long receiveCount = 1;
		
		try { 
			while(true) { 
				Socket so = this.ss.accept();
		        //logWrite(String.format("%s Agent Connect Count : %d, ", new Object[] { sdf.format(Calendar.getInstance().getTime()), Long.valueOf(receiveCount) }));
				logWrite(String.format("%s Agent Connect", new Object[] { sdf.format(Calendar.getInstance().getTime())}));
		        DirectoryLookup dl = new Process(this.BASE_DIR, so);
		        dl.start();
		        receiveCount++;
			}
		}catch(Exception e) {
			LOGGER.error(e);
			//e.printStackTrace();
			return;
		}
		
	}
	
	class Process extends DirectoryLookup {
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
	          sendString(this.socket.getOutputStream());
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
	        File f = new File(this.BASE_DIR);
	        File[] files = f.listFiles();
	        byte b;
	        int i;
	        File[] arrayOfFile1;
	        for (i = (arrayOfFile1 = files).length, b = 0; b < i; ) {
	          File file = arrayOfFile1[b];
	          if (file.isDirectory()) {
	            File[] taskList = file.listFiles();
	            byte b1;
	            int j;
	            File[] arrayOfFile2;
	            for (j = (arrayOfFile2 = taskList).length, b1 = 0; b1 < j; ) {
	              File logFile = arrayOfFile2[b1];
	              this.map.put(logFile.getName(), logFile);
	              b1++;
	            } 
	          } 
	          b++;
	        } 
	      }

	    /**
		 * 연결된 OutputStream 을 통해 File 을 전송한다.
		 */
	    public void receiveBinary(Socket socket) throws Exception {}

	    /**
		 * 연결된 OutputStream 을 통해 File 을 전송한다.
		 */
		public void sendString(OutputStream os) throws Exception {

			PrintWriter pw = new PrintWriter(os);
			List<File> completedList = new ArrayList<>();
			
			map.forEach((key, file) -> {
				try {
					BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "utf-8"));
					pw.println("FILE_PATH=" + file.getAbsolutePath());
					String str = new String();
					while ((str = br.readLine()) != null) {
						pw.println(str);
					}
					pw.flush();
					br.close();
					completedList.add(file);
				} catch (Exception e) {
					LOGGER.error(e);
					//e.printStackTrace();
				}
			});
			
			 postTrigger(completedList);
			
			 LOGGER.info("Successful End Task");
			//System.out.println("Successful End Task");
		}
		
		public void postTrigger(List<File> completedList) {
			/* TODO
			 * 1. 전송 파일 로그 기록
			 * 2. 디렉토리 삭제 + 파일 삭제
			 * 3. Queue 제거   
			 */
			
			completedList.forEach( (key) -> {
				// 1. 전송 파일 로그 기록 
				RealTimeExtenalServer.logWrite(String.format("%s Task Id : %s", sdf.format(Calendar.getInstance().getTime()), key.getAbsoluteFile()));
				
				// 2. 디렉토리 + 파일 삭제 
				try {
					key.delete();
					key.getParentFile().delete();
				} catch (Exception e) {
					LOGGER.error(e);
					//e.printStackTrace();
				}
				
				// 3. 큐 제거 
				this.map.remove(key.getName());
			});
			
		}


		@Override
		public void sendBinary(OutputStream os) throws Exception {}
	}
}

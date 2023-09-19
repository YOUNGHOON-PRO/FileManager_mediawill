package com.enders.server.bulk;

import com.enders.inf.BaseServer;
import com.enders.inf.DirectoryLookup;
import com.enders.utils.BaseFile;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
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
import java.util.StringTokenizer;
import org.zeroturnaround.zip.ZipUtil;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/*
 * 실시간 UMS 내외부간 파일 전송 매니저(내부 전용)
 * 1. Queue_0\LOG 하위 디렉터리를 Hooking 하고
 * 2. Socket 요청 시에 Socket Stream 을 통해 파일을 전송
 * 3. Post Trigger (파일 삭제, Log 기록) 수행  
 */
public class BulkExtenalServer extends BaseServer{
	
	private static final Logger LOGGER = LogManager.getLogger(BulkExtenalServer.class.getName());
		
	public int PORT = 0;
	public String BASE_DIR;
	public String SENDLOG_DIR;
	public String UNITLOG_DIR;
	public String ENVELOPE_DIR;
	public String RECEIVE_PATH;
	public String EXTRACT_DIR;
	public boolean ZIP_FILE_REMOVE;
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
	        props.load(getClass().getClassLoader().getResource("BulkExternal.properties").openStream());
	      } else {
	        props.load(new FileInputStream(Paths.get(System.getProperty("proppath"), new String[0]).toFile()));
	      } 
	      this.BASE_DIR = props.getProperty("SERVER.BASE_DIR");
	      this.PORT = Integer.parseInt(props.getProperty("SERVER.PORT"));
	      this.SENDLOG_DIR = props.getProperty("SERVER.SENDLOG_DIR");
	      this.UNITLOG_DIR = props.getProperty("SERVER.UNITLOG_DIR");
	      this.ENVELOPE_DIR = props.getProperty("SERVER.ENVELOPE_DIR");
	      this.RECEIVE_PATH = props.getProperty("RECEIVE.RECEIVE_PATH");
	      this.EXTRACT_DIR = props.getProperty("SERVER.EXTRACT_DIR");
	      this.ZIP_FILE_REMOVE = Boolean.valueOf(props.getProperty("SERVER.ZIP_FILE_REMOVE")).booleanValue();
	      LOG_FILE_PATH = props.getProperty("SERVER.LOG_FILE_PATH");
	    } catch (Exception e) {
	    	LOGGER.error(e);
	    	//e.printStackTrace();
	    } 
	  }

	public BulkExtenalServer() {
	    loadProperties();
	    LOGGER.info("::: BulkExtenalServer Server Started :::");
	    LOGGER.info("::: Listening Time : %s, Service Port %s::: ", new Object[] { sdf.format(Calendar.getInstance().getTime()), Integer.valueOf(this.PORT) });
	    //System.out.println("::: BulkExtenalServer Server Started :::");
	    //System.out.println(String.format("::: Listening Time : %s, Service Port %s::: ", new Object[] { sdf.format(Calendar.getInstance().getTime()), Integer.valueOf(this.PORT) }));
	    try {
	      this.ss = new ServerSocket(this.PORT);
	    } catch (Exception e) {
	    	LOGGER.error(e);
	    	//e.printStackTrace();
	    } 
	  }
	
	public void run() {
	    long receiveCount = 1L;
	    try {
	      while (true) {
	        Socket so = this.ss.accept();
	        //logWrite(String.format("%s Agent Connect Count : %d, ", new Object[] { sdf.format(Calendar.getInstance().getTime()), Long.valueOf(receiveCount) }));
	        logWrite(String.format("%s Agent Connect", new Object[] { sdf.format(Calendar.getInstance().getTime())}));
	        DirectoryLookup dl = new Process(this.BASE_DIR, so, this.SENDLOG_DIR, 
	            this.UNITLOG_DIR, this.EXTRACT_DIR, this.ENVELOPE_DIR);
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
	    String sendlogPath;
	    String unitlogPath;
	    String envelopePath;
	    String extractPath;
	    Map<String, File[]> map;
	    Socket socket;
	    final int MODE_SEND = 1;
	    final int MODE_RECEIVE = 2;
		
		// 파일 수신용
	    public Process(String root, Socket s) {
	        super(root);
	        this.map = (Map)new HashMap<>();
	        this.socket = s;
	      }
		
	    // 파일 전송용
	    public Process(String root, Socket s, String sendlogPath, String unitlogPath, String extractPath, String envelope) {
	        this(root, s);
	        this.sendlogPath = sendlogPath;
	        this.unitlogPath = unitlogPath;
	        this.envelopePath = envelope;
	        this.extractPath = extractPath;
	      }
		
	    /* 실제 처리부분 
		 * PUT / GET 모드에 따라 처리 
		 	1) PUT : 내부 -> 외부, 압축된 파일을 수신한다. (receiveBinary)
		   	- 압축된 파일이기에 binary 형태로 파일을 수신한다.
		   	- 여러번의 통신을 송수신을 진행한다. (모드/파일경로/파일크기/압축파일) 
		   	- 수신이 완료된 압축파일을 압축을 해제한다.
		   	- 처리상태를 마지막으로 응답한다.  
		  	2) GET : 외부 -> 내부, 발송로그를 전송한다. (sendString)
		  	- sendlog, unitlog 파일에 대해 sendlog 를 기준으로 전송한다. 
		  	- 이 경우는 char 형태로 파일을 전송한다. 
		 */
	    public void run() {
	        try {
	          InputStream is = this.socket.getInputStream();
	          OutputStream os = this.socket.getOutputStream();
	          BufferedReader br = new BufferedReader(new InputStreamReader(is));
	          String operator = br.readLine();
	          LOGGER.info("Operation :: " + operator);
	          //System.out.println("Operation :: " + operator);
	          if (operator.equals("PUT")) {
	            PrintWriter pw = new PrintWriter(os);
	            pw.println("OK");
	            pw.flush();
	            receiveBinary(this.socket);
	          } else if (operator.equals("GET")) {
	            lookup();
	            sendBinary(os, is);
	          } 
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
		 * SendLog 폴더의 파일목록을 조회하고, UnitLog 폴더에 동일한 파일이 있는 경우만 Queue 에 저장한다. 
		 */
	    public void lookup() {
	        BaseFile f = new BaseFile(String.valueOf(this.BASE_DIR) + this.sendlogPath);
	        File[] files = f.listFiles();
	        byte b;
	        int i;
	        File[] arrayOfFile1;
	        for (i = (arrayOfFile1 = files).length, b = 0; b < i; ) {
	          File sendlogFile = arrayOfFile1[b];
	          String fileName = sendlogFile.getName();
	          BaseFile unitlogFile = new BaseFile(String.valueOf(this.BASE_DIR) + this.unitlogPath + "/" + fileName);
	          BaseFile envelopeFile = new BaseFile(String.valueOf(this.BASE_DIR) + this.envelopePath + "/" + fileName);
	          if (!envelopeFile.exists()) {
	            File[] multiFile = new File[2];
	            multiFile[0] = sendlogFile;
	            multiFile[1] = (File)unitlogFile;
	            this.map.put(fileName, multiFile);
	          } 
	          b++;
	        } 
	      }

	    /**
		 * 연결된 OutputStream 을 통해 File 을 전송한다.
		 */
	    public void sendBinary(OutputStream os) throws Exception {}

	    
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
	            File[] files = this.map.get(key);
	            byte b;
	            int i;
	            File[] arrayOfFile1;
	            for (i = (arrayOfFile1 = files).length, b = 0; b < i; ) {
	              File file = arrayOfFile1[b];
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
	              fis.close();
	              accept = brs.readLine();
	              if (accept.equals("FINISH")) {
	            	  LOGGER.info("Response : Finished key : " +key);
	            	  //System.out.println("Response : Finished key : " +key);
	               
	                //======================================================
	                //20220420 중복이슈로인한 nullpoint 조치
	                if(completedList.contains(key)) {
	                	//중복
	                }else{
	                	completedList.add(key);	
	                }
	                //======================================================
	              } else {
	                throw new Exception(String.format("Response : BAD Response, detail : %s", new Object[] { accept }));
	              } 
	              b++;
	            } 
	          } catch (Exception e) {
	        	  LOGGER.error(e);
	        	  //e.printStackTrace();
	          } 
	        } 
	        os.close();
	        postTrigger(completedList, 1);
	        LOGGER.info("Successful End Task");
	        //System.out.println("Successful End Task");
	      }
	    
	    /**
		 * 연결된 OutputStream 을 통해 File 을 전송한다.
		 */
//	    public void sendString(OutputStream os) throws Exception {
//	        PrintWriter pw = new PrintWriter(os);
//	        List<String> completedList = new ArrayList<>();
//	        this.map.forEach((key, files) -> {
//	              try {
//	                File[] arrayOfFile;
//	                int i = (arrayOfFile = files).length;
//	                for (byte b = 0; b < i; b++) {
//	                  File f = arrayOfFile[b];
//	                  pw.println("FILE_PATH=" + f.getAbsolutePath());
//	                  BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "utf-8"));
//	                  String str = new String();
//	                  while ((str = br.readLine()) != null)
//	                	  pw.println(str); 
//	                  pw.flush();
//	                  br.close();
//	                } 
//	                completedList.add(key);
//	              } catch (Exception e) {
//	                e.printStackTrace();
//	              } 
//	            });
//	        postTrigger(completedList, 1);
//	        System.out.println("Successful End Task");
//	      }
	    
	    
//	    public void sendString2(OutputStream os) throws Exception {
//
//			PrintWriter pw = new PrintWriter(os);
//			List<String> completedList = new ArrayList<>();
//			
//			this.map.forEach((key, files) -> {
//				try {
//					for(File f : files) {
//						pw.println("FILE_PATH=" + f.getAbsolutePath());
//						BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "utf-8"));
//						String str = new String();
//						while ((str = br.readLine()) != null) {
//							pw.println(str);
//						}
//						pw.flush();
//						br.close();
//					}
//					
//					completedList.add(key);
//				} catch (Exception e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			});
//			
//			 postTrigger(completedList, MODE_SEND);
//			System.out.println("Successful End Task");
//		}
		
		public void postTrigger(List<String> completedList, int mode) {
		      if (completedList.size() == 0)
		        BulkExtenalServer.logWrite(String.format("%s Mode : %s, Task Id : %s", new Object[] { BulkExtenalServer.sdf.format(Calendar.getInstance().getTime()), (mode == 1) ? "SEND" : "RECEIVE", "No Data" })); 
		      completedList.forEach(key -> {
		            BulkExtenalServer.logWrite(String.format("%s Mode : %s, Task Id : %s", new Object[] { BulkExtenalServer.sdf.format(Calendar.getInstance().getTime()), (mode == 1) ? "SEND" : "RECEIVE", key }));
		            try {
		              if (mode == MODE_SEND) {
		                File[] files = this.map.get(key);
		                File[] arrayOfFile1;
		                int i = (arrayOfFile1 = files).length;
		                for (byte b = 0; b < i; b++) {
		                  File f = arrayOfFile1[b];
		                  try {
		                    f.delete();
		                  } catch (Exception e) {
		                	  LOGGER.error(e);
		                	  //System.out.println("POST TRIGGER ERROR : : " + e.getMessage());
		                  } 
		                } 
		                this.map.remove(key);
		              } else if (mode == MODE_RECEIVE && BulkExtenalServer.this.ZIP_FILE_REMOVE) {
		                File f = new File(key);
		                f.delete();
		              } 
		            } catch (Exception e) {
		            	LOGGER.error(e);
		            	//e.printStackTrace();
		            } 
		          });
		    }
		
//		public void postTrigger(List<String> completedList, int mode) {
//			/* TODO
//		 	* 1. 전송 파일 로그 기록 ( SEND, RECEIVE )
//		 	* 2. 디렉토리 삭제 + 파일 삭제 ( SEND ONLY ) 
//		 	* 3. Queue 제거   ( SEND ONLY )
//		 	*/
//			
//			if (completedList.size() == 0) { 
//				// 1. 占쏙옙占쏙옙 占쏙옙占쏙옙 占싸깍옙 占쏙옙占� 
//				BulkExtenalServer.logWrite(String.format("%s Mode : %s, Task Id : %s", sdf.format(Calendar.getInstance().getTime()), (mode == 1? "SEND" : "RECEIVE"), "No Data"));
//					
//			}
//			completedList.forEach( (key) -> {
//				// 1. 占쏙옙占쏙옙 占쏙옙占쏙옙 占싸깍옙 占쏙옙占� 
//				BulkExtenalServer.logWrite(String.format("%s Mode : %s, Task Id : %s", sdf.format(Calendar.getInstance().getTime()), (mode == 1? "SEND" : "RECEIVE"), key));
//				
//				try {
//					if ( mode == MODE_SEND) {
//		                File[] files = this.map.get(key);
//		                File[] arrayOfFile1;
//		                int i = (arrayOfFile1 = files).length;
//		                for (byte b = 0; b < i; b++) {
//		                  File f = arrayOfFile1[b];
//		                  try {
//		                    f.delete();
//		                  } catch (Exception e) {
//		                    System.out.println("POST TRIGGER ERROR : : " + e.getMessage());
//		                  } 
//		                } 
//		                this.map.remove(key);
//		              }else if (mode == MODE_RECEIVE && BulkExtenalServer.this.ZIP_FILE_REMOVE) {
//		                File f = new File(key);
//		                f.delete();
//		              } 
//				} catch (Exception e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			});
//			
//		}
		
		/*
		 * 내부 -> 외부, 압축된 파일을 수신한다.
		 */
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
		        lineString = br.readLine();
		        processTask = "Task : File Path";
		        if (lineString.indexOf("FILE_PATH=") > -1) {
		          String propString = lineString;
		          p = new Properties();
		          sr = new StringReader(propString);
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
		          sr = new StringReader(propString);
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
		        mybytearray = new byte[1024];
		        long current = 0L;
		        InputStream is = socket.getInputStream();
		        LOGGER.info("File Download Start.");
		        //System.out.println("File Download Start.");
		        int readBytes;
		        while ((readBytes = is.read(mybytearray, 0, mybytearray.length)) != -1) {
		          bos.write(mybytearray, 0, readBytes);
		          current += readBytes;
		          if (current >= fileLen)
		            break; 
		        } 
		        LOGGER.info("File Download Completed.");
		        //System.out.println("File Download Completed.");
		        bos.flush();
		        bos.close();
		        processTask = "Task : File Extract";
		        LOGGER.info("Zip File Extract .... ");
		        //System.out.println("Zip File Extract .... ");
		        ZipUtil.unpack(zipFile, new File(this.extractPath));
		        LOGGER.info("Zip File Extract Completed.");
		        //System.out.println("Zip File Extract Completed.");
		        sos.println("FINISH");
		        sos.flush();
		        completedList.add(filePath);
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
		        postTrigger(completedList, 2);
		      } 
		    }

		
		/*
		 * 경로에 따라 폴더를 생성한다.
		 */
		public String makeDirectory(String str) {
		      String base = BulkExtenalServer.this.RECEIVE_PATH;
		      StringTokenizer st = new StringTokenizer(str.replace(base, ""), "/");
		      String path = BulkExtenalServer.this.RECEIVE_PATH;
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
		  }
	
	public static void main(String[] ar) throws Exception {
	    int i = 0;
	    if (i == 0) {
	      BulkExtenalServer btes = new BulkExtenalServer();
	      btes.start();
	    } 
	  }
}

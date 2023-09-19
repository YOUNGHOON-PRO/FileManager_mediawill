package com.enders;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.zeroturnaround.zip.ZipUtil;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class test {
	
	private static final Logger LOGGER = LogManager.getLogger(test.class.getName());
	
	public void unCompress(File f) { 
		
	}
	
	public void taskZip(String taskId, Stream<Path> stream, String delimeter) throws Exception { 
		String fileName = String.format("d:\\logs\\%s.zip",taskId);
		File zipFile = new File(fileName);
		
		ZipUtil.createEmpty(zipFile);
		
		stream.forEach( (path) -> {
			
			String absPath = path.toString();
			int n = absPath.indexOf(delimeter);
			String compressPath = absPath.substring(n);
			
			try { 
				ZipUtil.addEntry(zipFile, compressPath, path.toFile());
			}catch(Exception e) {
				LOGGER.error(e);
				ZipUtil.pack(path.toFile(), zipFile);
				
			}
			
		});
	}

	public Stream<Path> findTaskFile(String base, String taskId) throws Exception {
		String suffix = "-";

		Stream<Path> stream = Files.find(Paths.get(base), 100, (path, attr) -> {
			boolean returnValue = false;
			String pathstring = path.toString();			
			
			if ( path.toFile().isDirectory() || pathstring.indexOf(taskId + suffix) == -1) return returnValue;			
			else return true;
			/*
			 * StringTokenizer st = new StringTokenizer(pathstring, "\\");
			 * while(st.hasMoreTokens()) { String next = st.nextToken();
			 * 
			 * if (!st.hasMoreTokens()) { if (next.indexOf(taskId + suffix) > -1) {
			 * System.out.println("matched token ::" + next); returnValue = true; break; } }
			 * } return returnValue;
			 */
		});

		return stream;
	}

	public static void main(String[] args)throws Exception {
		
		new test();
//		String taskId = "694";
//		Stream<Path> stream = t.findTaskFile("d:\\logs\\ums\\transfer\\", taskId);
//		t.taskZip(taskId, stream, "transfer");
		
		File f = new File("D:\\logs\\686.zip");
		
		ZipUtil.unpack(f, new File("d:\\logs\\"));
	}

}

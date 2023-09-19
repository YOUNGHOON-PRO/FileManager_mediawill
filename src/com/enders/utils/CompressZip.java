package com.enders.utils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.zeroturnaround.zip.FileSource;
import org.zeroturnaround.zip.ZipEntrySource;
import org.zeroturnaround.zip.ZipUtil;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CompressZip {
	
	private static final Logger LOGGER = LogManager.getLogger(CompressZip.class.getName());
	
	public File compressZipFile ;
	
	public File getZipFile() { 
		return this.compressZipFile;
	}
	
	public void taskZip(String taskId, Stream<Path> stream, String zipPath, String delimeter, boolean isDelete) throws Exception {
	    String fileName = String.format("%s/%s.zip", new Object[] { zipPath, taskId });
	    File zipFile = new File(fileName);
	    try {
	      ZipUtil.createEmpty(zipFile);
	      List<Path> list = (List<Path>) stream.collect((Collector)Collectors.toList());
	      ZipEntrySource[] addedEntries = new ZipEntrySource[list.size()];
	      for (int i = 0; i < list.size(); i++) {
	        File f = ((Path)list.get(i)).toFile();
	        int n = f.getAbsolutePath().indexOf(delimeter);
	        addedEntries[i] = (ZipEntrySource)new FileSource(f.getAbsolutePath().substring(n), f);
	      } 
	      ZipUtil.pack(addedEntries, zipFile);
	      this.compressZipFile = zipFile;
	      for (Path path : list) {
	        if (isDelete)
	          path.toFile().delete(); 
	        if (path.toFile().getParentFile().getName().startsWith(taskId))
	          path.toFile().getParentFile().delete(); 
	      } 
	    } catch (Exception e) {
	    	LOGGER.error(e);
	    	//e.printStackTrace();
	    } 
	  }

	
	public Stream<Path> findTaskFile(String base, String taskId, String suffix) throws Exception {
	    Stream<Path> stream = Files.find(Paths.get(base), 100, (path, attr) -> {
	          String pathstring = path.toString();
	          return path.toFile().getName().equals(taskId + ".zip") ? false : (!(path.toFile().isDirectory() || pathstring.indexOf(taskId + suffix) == -1));
	        });
	    
	    return stream;
	  }
	
	/*
	public Stream<Path> findTaskFile(String base, String taskId) throws Exception {
		String suffix = "-";

		Stream<Path> stream = Files.find(Paths.get(base), 100, (path, attr) -> {
			String pathstring = path.toString();			
			
			if ( path.toFile().isDirectory() || pathstring.indexOf(taskId + suffix) == -1) return false;
			else return true;
			
		});

		return stream;
	}
	*/
	
	public Stream<Path> findUnknowFile(String base) throws Exception {
		Stream<Path> stream = Files.find(Paths.get(base), 100, (path, attr) -> {
			return true;
		});

		return stream;
	}
}

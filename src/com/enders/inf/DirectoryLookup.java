package com.enders.inf;

import java.io.OutputStream;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class DirectoryLookup extends Thread {

	private static final Logger LOGGER = LogManager.getLogger(DirectoryLookup.class.getName());
	
	protected String BASE_DIR;
	
	public DirectoryLookup(String root) { 
		this.BASE_DIR = root;
		LOGGER.info("Direcotry Path : "+ this.BASE_DIR);
		//System.out.println("Direcotry Path : "+ this.BASE_DIR);
	}
	
	public abstract void lookup(); 
	
	public abstract void sendBinary(OutputStream paramOutputStream) throws Exception;
	  
	public abstract void receiveBinary(Socket paramSocket) throws Exception;
}

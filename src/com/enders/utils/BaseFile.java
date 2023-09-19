package com.enders.utils;

import java.io.File;

public class BaseFile extends File{ 
	private static final long serialVersionUID = 8520971796381679800L;
	
	String taskId;
	String path;
	
	public BaseFile(String pathname) { 
		super(pathname);
	}
	
	public BaseFile(File parent, String child) {
		super(parent, child);
		// TODO Auto-generated constructor stub
	}
	
	public String getTaskId() {
		return this.taskId;
	}
	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}
}

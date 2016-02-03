/*
 * 
 * 
 * Copyright 2016 Rachid Boudjelida <rachidboudjelida@gmail.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package deodex.controlers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.JProgressBar;

import deodex.obj.JarObj;
import deodex.tools.Deodexer;
import deodex.tools.FilesUtils;
import deodex.tools.ZipTools;
import deodex.ui.LoggerPan;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;

public class JarWorker implements Runnable{

	ArrayList<File> odexFiles = new ArrayList<File>();
	LoggerPan logPan;
	File tmpFolder;
	JProgressBar progressBar;
	public JarWorker(ArrayList<File> odexList, LoggerPan logPan, File tmpFolder ){
		this.odexFiles = odexList;
		this.logPan = logPan;
		this.tmpFolder = tmpFolder;
		this.progressBar = new JProgressBar();
		progressBar.setMinimum(0);
		progressBar.setMaximum(odexList.size());
		
	}
	@Override
	public void run() {
		// TODO Auto-generated method stub
		int i = 0;
		for (File jar : odexFiles){
			boolean success = deodexJar(jar);
			if(success){
				// TODO log Sucess
			} else {
				// TODO log FAILED
			}
			this.progressBar.setValue(i++);
		}
	}

	private boolean deodexJar(File odex){
		JarObj jar = new JarObj(odex);
		boolean copyStatus = jar.copyNeedFiles(tmpFolder);
		if(!copyStatus){
			// TODO add loggin for this
			return false;
		} else {
			boolean extractStatus = false;
			try {
				extractStatus = ZipTools.extractOdex(jar.getTmpodex());
			} catch (IOException e) {
				// TODO Auto-generated catch block XXX : add logging for this
				e.printStackTrace(); 
				return false;
			}
			if(!extractStatus){
				// TODO add logging for this
				return false;
			} else {
				boolean deodexStatus = false;
				deodexStatus = Deodexer.deodexApk(jar.getTmpodex(), jar.getTmpdex());
				if(!deodexStatus){
					//TODO : add LOGGIN for this
					return false;
				} else {
					boolean rename = false;
					rename = FilesUtils.copyFile(jar.getTmpdex(), jar.getTmpClasses());
					if(jar.getTmpdex2().exists()){
						rename = rename && FilesUtils.copyFile(jar.getTmpdex2(), jar.getTmpClasses2());
					}
					rename = jar.getTmpdex2().exists() ?  
							jar.getTmpClasses().exists()&& jar.getTmpClasses2().exists(): jar.getTmpClasses().exists() ;
					if(!rename){
						// TODO : add log to this
						return false;
					} else {
						ArrayList<File> list = new ArrayList<File>();
						list.add(jar.getTmpClasses());
						if (jar.getTmpClasses2().exists()){
							list.add(jar.getTmpClasses2());
						}
						ZipTools.addFilesToZip(list, jar.getTmpJar());
						boolean addstatus = false;
						try {
							addstatus = ZipTools.isFileinZip(jar.getTmpClasses().getName(), new ZipFile(jar.getTmpJar()));
							if(list.size() > 1){
								addstatus = addstatus && ZipTools.isFileinZip(jar.getTmpClasses2().getName(), new ZipFile(jar.getTmpJar()));
							}
						} catch (ZipException e) {
							e.printStackTrace();
						}
						if(!addstatus){
							// TODO add logging for this
							return false;
						} else {
							boolean putBack = false;
							putBack = FilesUtils.copyFile(jar.getTmpJar(), jar.getOrigJar());
							if(!putBack){
								//TODO : add LOGGING to this
								return false;
							}
						}
					}
				}
			}
		}
		FilesUtils.deleteRecursively(jar.getTmpFolder());
		FilesUtils.deleteRecursively(jar.getOdexFile());
		
		
		return true;
	}
}

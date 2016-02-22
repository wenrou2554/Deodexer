/*
 *  Lordroid One Deodexer To Rule Them All
 * 
 *  Copyright 2016 Rachid Boudjelida <rachidboudjelida@gmail.com>
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package deodex.controlers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.JProgressBar;

import deodex.R;
import deodex.S;
import deodex.SessionCfg;
import deodex.obj.JarObj;
import deodex.tools.Deodexer;
import deodex.tools.FilesUtils;
import deodex.tools.Zip;
import deodex.tools.ZipTools;

public class JarWorker implements Runnable, Watchable {

	ArrayList<File> odexFiles = new ArrayList<File>();

	LoggerPan logPan;
	File tmpFolder;
	JProgressBar progressBar;
	private ThreadWatcher threadWatcher;

	public JarWorker(ArrayList<File> odexList, LoggerPan logPan, File tmpFolder) {
		this.odexFiles = odexList;
		this.logPan = logPan;
		this.tmpFolder = tmpFolder;
		this.progressBar = new JProgressBar();
		progressBar.setMinimum(0);
		progressBar.setMaximum(odexList.size() * 6);
		progressBar.setStringPainted(true);
		tmpFolder.mkdirs();
	}

	@Override
	public void addThreadWatcher(ThreadWatcher watcher) {
		// TODO Auto-generated method stub
		this.threadWatcher = watcher;

	}

	private boolean deodexJar(File odex) {
		JarObj jar = new JarObj(odex);
		// phase 1
		boolean copyStatus = jar.copyNeedFiles(tmpFolder);
		if (!copyStatus) {
			// TODO add loggin for this
			this.logPan.addLog(
					R.getString(S.LOG_ERROR) + "[" + jar.getOrigJar() + "]" + R.getString("log.copy.to.tmp.failed"));
			return false;
		}
		this.progressBar.setValue(this.progressBar.getValue() + 1);
		progressBar.setString(R.getString("progress.jar") + " " + this.getPercent() + "%");
		threadWatcher.updateProgress();

		// phase 02
		boolean extractStatus = false;
		try {
			extractStatus = ZipTools.extractOdex(jar.getTmpCompodex());
		} catch (IOException e) {
			this.logPan.addLog(
					R.getString(S.LOG_ERROR) + "[" + jar.getOrigJar() + "]" + R.getString("log.extract.to.tmp.failed"));
			e.printStackTrace();
			return false;
		}
		if (!extractStatus) {
			this.logPan.addLog(
					R.getString(S.LOG_ERROR) + "[" + jar.getOrigJar() + "]" + R.getString("log.extract.to.tmp.failed"));
			return false;
		}
		this.progressBar.setValue(this.progressBar.getValue() + 1);
		progressBar.setString(R.getString("progress.jar") + " " + this.getPercent() + "%");
		threadWatcher.updateProgress();

		// phase 3
		boolean deodexStatus = false;
		deodexStatus = Deodexer.deodexApk(jar.getTmpodex(), jar.getTmpdex());
		if (!deodexStatus) {
			deodexStatus = Deodexer.deodexApkFailSafe(jar.getTmpodex(), jar.getTmpdex());
			if (!deodexStatus) {
				this.logPan.addLog(
						R.getString(S.LOG_ERROR) + "[" + jar.getOrigJar() + "]" + R.getString("log.deodex.failed"));
				return false;
			}
		}
		this.progressBar.setValue(this.progressBar.getValue() + 1);
		progressBar.setString(R.getString("progress.jar") + " " + this.getPercent() + "%");
		threadWatcher.updateProgress();

		// phase 4
		boolean rename = false;
		rename = jar.getTmpdex().renameTo(jar.getTmpClasses());
		if (jar.getTmpdex2().exists()) {
			rename = rename && jar.getTmpdex2().renameTo(jar.getTmpClasses2());
		}
		if (jar.getTmpdex3().exists()) {
			rename = rename && jar.getTmpdex3().renameTo(jar.getTmpClasses3());
		}

		// if(rename) return true;
		if (!rename) {
			this.logPan.addLog(
					R.getString(S.LOG_ERROR) + "[" + jar.getOrigJar() + "]" + R.getString("log.classes.failed"));
			return false;
		}
		this.progressBar.setValue(this.progressBar.getValue() + 1);
		progressBar.setString(R.getString("progress.jar") + " " + this.getPercent() + "%");
		threadWatcher.updateProgress();

		// phase 5
		ArrayList<File> list = new ArrayList<File>();
		list.add(jar.getTmpClasses());
		if (jar.getTmpClasses2().exists()) {
			list.add(jar.getTmpClasses2());
		}
		if (jar.getTmpClasses3().exists()) {
			list.add(jar.getTmpClasses3());
		}
		boolean addstatus = false;
		try {
			addstatus = Zip.addFilesToExistingZip(jar.getTmpJar(), list);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		if (!addstatus) {
			this.logPan.addLog(
					R.getString(S.LOG_ERROR) + "[" + jar.getOrigJar() + "]" + R.getString("log.add.classes.failed"));
			return false;
		}
		this.progressBar.setValue(this.progressBar.getValue() + 1);
		progressBar.setString(R.getString("progress.jar") + " " + this.getPercent() + "%");
		threadWatcher.updateProgress();

		// phase 6
		boolean putBack = false;
		putBack = FilesUtils.copyFile(jar.getTmpJar(), jar.getOrigJar());
		if (!putBack) {
			this.logPan.addLog(
					R.getString(S.LOG_ERROR) + "[" + jar.getOrigJar() + "]" + R.getString("log.putback.apk.failed"));
			return false;
		}

		FilesUtils.deleteRecursively(jar.getTmpFolder());
		FilesUtils.deleteRecursively(jar.getOdexFile());
		FilesUtils.deleteFiles(FilesUtils.searchExactFileNames(
				new File(SessionCfg.getSystemFolder() + File.separator + S.SYSTEM_FRAMEWORK),
				jar.getOdexFile().getName()));
		return true;
	}

	private int getPercent() {
		// max ===> 100
		// value ===> ?
		// ? = value*100/max;
		return (this.progressBar.getValue() * 100) / this.progressBar.getMaximum();

	}

	/**
	 * @return the progressBar
	 */
	public JProgressBar getProgressBar() {
		return progressBar;
	}

	@Override
	public void run() {
		if (this.odexFiles != null && this.odexFiles.size() > 0) {
			for (File jar : odexFiles) {
				boolean success = deodexJar(jar);
				if (success) {
					logPan.addLog(
							R.getString(S.LOG_INFO) + "[" + new JarObj(jar).getAbsoluteName() + ".jar]" + " [SUCCESS]");
				} else {
					logPan.addLog(
							R.getString(S.LOG_WARNING) + "[" + new JarObj(jar).getAbsoluteName() + ".jar]" + " [FAILED]");
				}
				this.progressBar.setValue(this.progressBar.getValue() + 1);
				progressBar.setString(R.getString("progress.jar") + " " + this.getPercent() + "%");
				threadWatcher.updateProgress();
			}
		}

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		FilesUtils.deleteRecursively(tmpFolder);
		this.progressBar.setValue(this.progressBar.getMaximum());
		progressBar.setString(R.getString("progress.done"));
		this.threadWatcher.updateProgress();
		this.threadWatcher.done(this);
	}
}

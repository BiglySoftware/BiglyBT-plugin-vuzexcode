package com.vuze.transcoder;

import java.io.File;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.pif.PluginInterface;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;

import com.vuze.transcoder.media.MediaAnalyser;

public class TranscodeWindow implements TranscodeListener {
	
	Display display;
	Shell shell;
	ProgressBar pb;
	Label info;
	Button cancel;
	
	TranscodeOperation operation;
	
	public TranscodeWindow(
			String inputFileName,
			String outputFileName,
			TranscodeProfile profile,
			String[] ffmpegPaths,
			String mediainfoPath) 
	{
		this( null, inputFileName, outputFileName, profile, ffmpegPaths, mediainfoPath );
	}
	
	public TranscodeWindow(
			final PluginInterface	plugin_interface,
			final String inputFileName,
			final String outputFileName,
			final TranscodeProfile profile,
			final String[] ffmpegPaths,
			final String mediainfoPath) {
		
		Shell mainShell = Utils.findAnyShell();
		if(mainShell != null) {
			shell = ShellFactory.createShell(mainShell,SWT.TITLE);
		} else {
			shell = ShellFactory.createShell(SWT.TITLE);
		}
		shell.setText("Converting");
		display = shell.getDisplay();
		shell.setLayout(new FormLayout());
		
		if(mainShell != null) {
			Utils.centerWindowRelativeTo(shell, mainShell);
		}
		
		Label jobDescription = new Label(shell,SWT.WRAP);
		File f = new File(inputFileName);
		String shortName = f.getName();
		jobDescription.setText("Converting " + shortName + " to " + profile.getName());
		
		pb = new ProgressBar(shell,SWT.HORIZONTAL);
		
		info = new Label(shell,SWT.NONE);
		info.setText("Initializing");
		
		cancel = new Button(shell,SWT.PUSH);
		cancel.setText("Cancel");
		cancel.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				if(operation != null) {
					operation.cancel();
				} else {
					shell.close();
				}
			}
		});
		
		FormData data;
		
		data = new FormData();
		data.left = new FormAttachment(0,5);
		data.top = new FormAttachment(0,5);
		data.right = new FormAttachment(100,-5);
		jobDescription.setLayoutData(data);
		
		data = new FormData();
		data.left = new FormAttachment(0,5);
		data.top = new FormAttachment(jobDescription,5);
		data.right = new FormAttachment(100,-5);
		pb.setLayoutData(data);
		
		data = new FormData();
		data.left = new FormAttachment(0,5);
		data.top = new FormAttachment(pb,5);
		data.right = new FormAttachment(100,-5);
		info.setLayoutData(data);
		
		data = new FormData();
		data.width = 80;
		data.bottom = new FormAttachment(100,-5);
		data.right = new FormAttachment(100,-5);
		cancel.setLayoutData(data);
		
		shell.setSize(500, 150);
		shell.layout();
		shell.open();
		
		AEThread2 go = new AEThread2("xcode launcher",true) {
			public void run() {
				try {
					Transcoder transcoder = new Transcoder(plugin_interface,ffmpegPaths,inputFileName);
					
					MediaAnalyser analyser = new MediaAnalyser(null,mediainfoPath);
					
					transcoder.context.mediaInformation = analyser.analyse(inputFileName);
					
					operation = transcoder.transcode(TranscodeWindow.this, profile, outputFileName);
					
				} catch(Exception e) {
					e.printStackTrace();
					
				}
			}
		};
		go.start();
		
		
		
	}
	
	
	public void reportDone() {
		if(!display.isDisposed()) display.asyncExec(new Runnable() {
			public void run() {
				info.setText("Complete");
				operation = null;
				cancel.setText("Close");
			}
		});
	}
	
	public void reportFailed( Throwable t, boolean is_perm ) {
		if(!display.isDisposed()) display.asyncExec(new Runnable() {
			public void run() {
				info.setText("Failed");
				operation = null;
				cancel.setText("Close");
			}
		});
	}
	
	public void reportProgress(final TranscodeContext context,final int currentFrame,
			final int nbFrames,final int framesPerSec) {
		if(!display.isDisposed()) display.asyncExec(new Runnable() {
			public void run() {
				pb.setMaximum(nbFrames);
				pb.setSelection(currentFrame);
				if(operation != null) {
					info.setText("Remaining : " + DisplayFormatters.formatETA(operation.getETA()));
				}
			}
		});
	}
	
	public void reportNewDimensions(int width, int height) {		
	}
	
	public void log(String str) {
	}
}

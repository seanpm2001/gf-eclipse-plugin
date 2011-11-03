/**
 * GF Eclipse Plugin
 * http://www.grammaticalframework.org/eclipse/
 * John J. Camilleri, 2011
 * 
 * The research leading to these results has received funding from the
 * European Union's Seventh Framework Programme (FP7/2007-2013) under
 * grant agreement n° FP7-ICT-247914.
 */
package org.grammaticalframework.eclipse.launch;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.LaunchConfigurationDelegate;
import org.grammaticalframework.eclipse.GFPreferences;


// TODO: Auto-generated Javadoc
/**
 * The Class GFLaunchConfigurationDelegate.
 */
public class GFLaunchConfigurationDelegate extends LaunchConfigurationDelegate {

	/**
	 * The Constant log.
	 */
	private static final Logger log = Logger.getLogger(GFLaunchConfigurationDelegate.class);

	/**
	 * Sets the console.
	 *
	 * @param ps the new console
	 */
	public static void setConsole(PrintStream ps) {
		System.setOut(ps);
		System.setErr(ps);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.ILaunchConfigurationDelegate#launch(org.eclipse.debug.core.ILaunchConfiguration, java.lang.String, org.eclipse.debug.core.ILaunch, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void launch(ILaunchConfiguration configuration, String mode,
			ILaunch launch, IProgressMonitor monitor) throws CoreException {

		// Look here for some hints:
		// http://code.google.com/p/goclipse/source/browse/trunk/goclipse-n/src/com/googlecode/goclipse/debug/LaunchConfigurationDelegate.java?r=64
		
//		String gfPath = prefs.getString(GFPreferences.QUALIFIER, GFPreferences.GF_BIN_PATH, (String)null, null);
		String gfPath = GFPreferences.getString(GFPreferences.GF_BIN_PATH);
		String wdir = configuration.getAttribute(IGFLaunchConfigConstants.WORKING_DIR, (String)null);
		String options = configuration.getAttribute(IGFLaunchConfigConstants.OPTIONS, (String)null);
		String files = configuration.getAttribute(IGFLaunchConfigConstants.FILENAMES, (String)null);
		if (gfPath == null || gfPath.trim().isEmpty()) {
			log.error("Cannot start launch: GF path not specified.");
			return;
		}
		if (files == null || files.trim().isEmpty()) {
			log.error("Cannot start launch: No filenames specified.");
			return;
		}
		ArrayList<String> command = new ArrayList<String>();
		command.add(gfPath);
		command.add("--batch");
		command.addAll( Arrays.asList(options.split("\\s")) );
		command.addAll( Arrays.asList(files.split("\\s")) );
		
		try {
			StringBuilder sb = new StringBuilder();
	    	sb.append("Running:");
		    for (String s : command) {
		    	sb.append(" ");
		    	sb.append(s);
		    }
		    log.info(sb.toString());
		    		
			ProcessBuilder b = new ProcessBuilder(command);
			b.directory(new File(wdir));
			b.redirectErrorStream(true);
			Process process = b.start();
		    
			BufferedReader processOutput = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String ls_str;
			while ((ls_str = processOutput.readLine()) != null) {
				log.debug(ls_str);
			}
			
			processOutput.close();
			process.waitFor();
			
		} catch (IOException e) {
			log.error("Error: " + e.getMessage());
		} catch (InterruptedException e) {
			log.error("Interrupted: " + e.getMessage());
		} finally {
			monitor.done();
		}
		
	}

}

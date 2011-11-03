/**
 * GF Eclipse Plugin
 * http://www.grammaticalframework.org/eclipse/
 * John J. Camilleri, 2011
 * 
 * The research leading to these results has received funding from the
 * European Union's Seventh Framework Programme (FP7/2007-2013) under
 * grant agreement n° FP7-ICT-247914.
 */
package org.grammaticalframework.eclipse.ui.perspectives;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

// TODO: Auto-generated Javadoc
/**
 * The Class GFConsole.
 */
public class GFConsole {
	
	/**
	 * The console.
	 */
	private static MessageConsole console = null;
	
	/**
	 * The stream.
	 */
	private static MessageConsoleStream stream = null;
	
	/**
	 * Gets the console.
	 *
	 * @return the console
	 */
	public static MessageConsole getConsole() {
		if (console == null) {
			ImageDescriptor image = ImageDescriptor.createFromFile(null, "icons/gf-console.png");
			console = new MessageConsole("GF Compiler", image);
			console.activate();
			ConsolePlugin.getDefault().getConsoleManager().addConsoles(new IConsole[]{ console });
		}
		return console;
	} 
	
	/**
	 * Gets the stream.
	 *
	 * @return the stream
	 */
	public static MessageConsoleStream getStream() {
		if (stream == null) {
			stream = getConsole().newMessageStream();
		}
		return stream;
	}
	
	/**
	 * Clear.
	 */
	public static void clear() {
		console.clearConsole();
	}

}

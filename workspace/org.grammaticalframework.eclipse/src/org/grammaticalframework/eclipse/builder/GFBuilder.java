/**
 * GF Eclipse Plugin
 * http://www.grammaticalframework.org/eclipse/
 * John J. Camilleri, 2011
 * 
 * The research leading to these results has received funding from the
 * European Union's Seventh Framework Programme (FP7/2007-2013) under
 * grant agreement n° FP7-ICT-247914.
 */
package org.grammaticalframework.eclipse.builder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.grammaticalframework.eclipse.GFPreferences;
import org.grammaticalframework.eclipse.scoping.GFScopeProvider;
import org.apache.log4j.Logger;

/**
 * Custom GF builder, yeah!
 * Some refs..
 * 	http://wiki.eclipse.org/FAQ_How_do_I_implement_an_incremental_project_builder%3F
 * 	http://www.eclipse.org/articles/Article-Builders/builders.html
 * 
 * @author John J. Camilleri
 *
 */
public class GFBuilder extends IncrementalProjectBuilder {

	/**
	 * The Constant BUILDER_ID.
	 */
	public static final String BUILDER_ID = "org.grammaticalframework.eclipse.ui.build.GFBuilderID"; //$NON-NLS-1$

	/**
	 * The Constant BUILD_FOLDER.
	 */
	public static final String BUILD_FOLDER = ".gfbuild"; //$NON-NLS-1$
	
	/**
	 * The Constant BUILD_FOLDER.
	 */
	public static final String EXTERNAL_FOLDER = "(External)"; //$NON-NLS-1$

	/**
	 * Use tag based scoping?
	 */
//	public static final Boolean TAG_BASED_SCOPING = true;
	
	/**
	 * Clean old tags fiel before rebuilding?
	 */
	private static final Boolean CLEAN_BEFORE_BUILD = false;

	/**
	 * The GF paths.
	 */
	private String gfPath;
	private String gfLibPath;

	/**
	 * The Constant log.
	 */
	private static final Logger log = Logger.getLogger(GFBuilder.class);
	
	/**
	 * The main build method
	 * 
	 * After completing a build, this builder may return a list of projects for which it requires a resource delta the next time it is run.
	 * This builder's project is implicitly included and need not be specified. The build mechanism will attempt to maintain and compute
	 * deltas relative to the identified projects when asked the next time this builder is run. Builders must re-specify the list of interesting 
	 * projects every time they are run as this is not carried forward beyond the next build. Projects mentioned in return value but which
	 * do not exist will be ignored and no delta will be made available for them.
	 */
	/* (non-Javadoc)
	 * @see org.eclipse.core.resources.IncrementalProjectBuilder#build(int, java.util.Map, org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		// Get some prefs
		gfPath = GFPreferences.getRuntimePath();
		if (gfPath == null || gfPath.trim().isEmpty()) {
			log.error("Error during build: GF path not specified.");
			return null;
		}
		gfLibPath = GFPreferences.getLibraryPath();
		
		try {
			if (kind == IncrementalProjectBuilder.FULL_BUILD) {
				fullBuild(monitor);
			} else {
				// Is doing a full build for every incremental change overkill?
				// The reason we have it is when changes in your file affect teh scoping of another file
				// TODO Solution: only rebuild files whos tags contain something from the file being rebuilt
				fullBuild(monitor);
//				IResourceDelta delta = getDelta(getProject());
//				if (delta == null) {
//					fullBuild(monitor);
//				} else {
//					incrementalBuild(delta, monitor);
//				}
			}
			
			// Force project refresh
			getProject().refreshLocal(IResource.DEPTH_INFINITE, monitor);
			
		} catch (OperationCanceledException e) {
			log.info("Build cancelled");
			throw e;
		}
		
		return null;
	}

	/**
	 * Incremental build.
	 *
	 * @param delta the delta
	 * @param monitor the monitor
	 */
	@SuppressWarnings("unused")
	private void incrementalBuild(IResourceDelta delta, final IProgressMonitor monitor) throws OperationCanceledException {
		log.info("Incremental build on " + delta.getResource().getName());
		try {
			delta.accept(new IResourceDeltaVisitor() {
				public boolean visit(IResourceDelta delta) {
					
					// Check for cancellation
					if (monitor.isCanceled()) {
						throw new OperationCanceledException();
					}
					
					// Get ahold of resource, build if necessary
					IResource resource = delta.getResource();
					int kind = delta.getKind(); 
					if (kind == IResourceDelta.ADDED || kind == IResourceDelta.CHANGED) {
						if (shouldBuild(resource)) {
							IFile file = (IFile) resource;
							if (buildFile(file)) {
								log.info("+ " + delta.getResource().getRawLocation());
							} else {
								log.warn("✕ " + delta.getResource().getRawLocation());
							}
							
						}
						
					}
					
					// Visit children too
					return true;
				}
			});
			
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}


	/**
	 * Full build.
	 *
	 * @param monitor the monitor
	 * @throws CoreException the core exception
	 */
	private void fullBuild(final IProgressMonitor monitor) throws OperationCanceledException, CoreException {
		log.info("Full build on " + getProject().getName());
		recursiveDispatcher(getProject().members(), new CallableOnResource() {
			public void call(IResource resource) {

				// Check for cancellation
				if (monitor.isCanceled()) {
					throw new OperationCanceledException();
				}
				
				// Build if necessary
				if (shouldBuild(resource)) {
					if (buildFile((IFile) resource)) {
						log.info("+ " + resource.getRawLocation());
					} else {
						log.warn("✕ " + resource.getRawLocation());
					}
				}
			}
		});
	}
	
	/**
	 * Clean Project (invoked by user via menu)
	 */
	@Override
	protected void clean(final IProgressMonitor monitor) throws CoreException {
		log.info("Clean " + getProject().getName());
		
		// Delete all markers
		getProject().deleteMarkers(null, true, IResource.DEPTH_INFINITE);
		
		// Iterate over all files in project, decide what to do with them
		recursiveDispatcher(getProject().members(), new CallableOnResource() {
			public void call(IResource resource) {
				// Check for cancellation
				if (monitor.isCanceled()) {
					throw new OperationCanceledException();
				}
				// Get some details
				boolean isFile = (resource.getType() == IResource.FILE);
				String extension;
				if ((extension = resource.getFileExtension()) == null) extension = "";
				
				// Decide if we want to delete it
				boolean delete = false;
				if (isFile && extension.equals("gfo")) {
					delete = true;
				}
				else {
					try {
						IContainer parent = resource.getParent();
						delete = parent.getName().equals(BUILD_FOLDER);
					} catch (NullPointerException _) {
						// file has no grand/parent
					}
				}
				
				// Do the deed
				try {
					if (delete) {
						resource.delete(true, monitor);
						log.info("- " + resource.getRawLocation());
					}
				} catch (CoreException e) {
					log.warn("✕ " + resource.getRawLocation());
				}
				
			}
		});
	}
	
	/**
	 * Clean all the files in the build directory related to the given file.
	 *
	 * @param file the file
	 */
	private void cleanFile(IFile file) {
		File tagsFile = new File( getTagsFile(file) );
		if (tagsFile.exists()) {
			try {
				tagsFile.delete();
				log.info("- " + tagsFile.getAbsolutePath());
			} catch (Exception _) {
				log.warn("✕ " + tagsFile.getAbsolutePath());
			}
		}
	}
	
	/**
	 * For recursively applying a function to an IResource.
	 */
	interface CallableOnResource {
		public void call(IResource resource);
	}
	
	/**
	 * Recursive dispatcher.
	 *
	 * @param res the res
	 * @param func the func
	 */
	private void recursiveDispatcher(IResource[] res, CallableOnResource func) {
		try {
			for (IResource r : res) {
				if (r.getType() == IResource.FOLDER) {
					recursiveDispatcher(((IFolder)r).members(), func);
				} else {
					func.call(r);
				}
			}
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Determine if a resource should be built, based on its properties.
	 *
	 * @param resource the resource
	 * @return true, if this is a source fiel which should be built
	 */
	private boolean shouldBuild(IResource resource) {
		try {
			boolean isFile = resource.getType() == IResource.FILE;
			boolean isGF = resource.getFileExtension().equals("gf");
			boolean notInBuildFolder = !resource.getFullPath().toOSString().contains(BUILD_FOLDER);
			boolean notInExternalFolder = !resource.getFullPath().toOSString().contains(EXTERNAL_FOLDER);
			return isFile && isGF && notInBuildFolder && notInExternalFolder;
		} catch (NullPointerException _) {
			return false;
		}
	}
	
	/**
	 * Gets the builds the directory.
	 *
	 * @param file the file
	 * @return the builds the directory
	 */
	public static String getBuildSubfolder(String sourceFileName) {
		return getBuildSubfolder(sourceFileName, false);
	}
	public static String getBuildSubfolder(String sourceFileName, boolean useIndividualFolders) {
		if (useIndividualFolders) {
			int dotIx = sourceFileName.lastIndexOf('.');
			return BUILD_FOLDER
					+ java.io.File.separator
					+ ((dotIx > 0) ? sourceFileName.substring(0, dotIx) : sourceFileName)
					+ java.io.File.separator;
		} else {
			return BUILD_FOLDER
				+ java.io.File.separator;
		}
	}
	private String getBuildDirectory(IFile file) {
		return getBuildDirectory(file, false);
	}
	private String getBuildDirectory(IFile file, boolean useIndividualFolders) {
		String filename = file.getName();
		return file.getRawLocation().removeLastSegments(1).toOSString()
				+ java.io.File.separator
				+ getBuildSubfolder(filename, useIndividualFolders);
	}
	public static String getTagsFile(String sourceFileName) {
		return BUILD_FOLDER
				+ java.io.File.separator
				+ sourceFileName + "-tags";
	}
	public static String getTagsFile(IFile file) {
		return file.getRawLocation().removeLastSegments(1).toOSString()
				+ java.io.File.separator
				+ getTagsFile(file.getName());
	}
	
	/**
	 * Call the respective build method depending on the type of build
	 * @param file
	 * @return
	 */
	private boolean buildFile(IFile file) {
		// Clean up first
		if (CLEAN_BEFORE_BUILD)
			cleanFile(file);
		try {
			file.deleteMarkers(null, true, IResource.DEPTH_ZERO);
		} catch(CoreException _) {
			log.warn("Error trying to delete markers for " + file.getName());
		}
		
		// Invalidate scoping cache
		GFScopeProvider.cacheDirtyState.put(file.getName(), true);
		
		// Do it
		return buildFileTAGS(file);
	}
	
	/**
	 * For a single .gf file, compile it with the GF -tags flag which outputs
	 * a single tags file.
	 *
	 * @param file the file
	 * @return true, if successful
	 */
	private boolean buildFileTAGS(IFile file) {

		String filename = file.getName();
		String workingDir = file.getRawLocation().removeLastSegments(1).toOSString() + java.io.File.separator;
		String buildDir = getBuildDirectory(file);

		try {
			// Check the build directory and try to create it
			File workingDirFile = new File(workingDir);
			File buildDirFile = new File(buildDir);
			if (!buildDirFile.exists()) {
				buildDirFile.mkdir();
			}

			// Compile to get tags with: gf --tags HelloEng.gf
			ArrayList<String> command = new ArrayList<String>();
			command.add(gfPath);
			command.add("--v=0"); // quiet - errors are still displayed
			command.add("--tags");
			command.add("--output-dir=" + buildDir);
//			if (gfLibPath != null && !gfLibPath.isEmpty()) {
//				command.add(String.format("--gf-lib-path=\"%s\"", gfLibPath)); // Use library path in command (if supplied)
//			}
			command.add(filename);

			// Execute command
			ProcessBuilder pbTags = new ProcessBuilder(command);
			pbTags.directory(workingDirFile);
			pbTags.redirectErrorStream(false);
			Process procTags = pbTags.start();
			
			// Consume & log all output
			BufferedReader processOutput = new BufferedReader(new InputStreamReader(procTags.getInputStream()));
			String out_str;
			while ((out_str = processOutput.readLine()) != null) {
				log.debug("GF: " + out_str);
			}
			
			// If compile failed, parse error messages and add markers
			if (procTags.waitFor() != 0) {
				parseGFErrorStream(file, procTags.getErrorStream());
				return false;
			}
			
			// Done
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	/**
	 * Separate method for parsing the GF error stream and adding markers as necessary
	 * 
	 * TODO Track indentation in errors, there might be multiple separate errors!
	 * 
	 * @param file
	 * @param errStream
	 */
	private void parseGFErrorStream(IFile file, InputStream errStream) {
		BufferedReader processError = new BufferedReader(new InputStreamReader(errStream));
		
		try {

			// Stick all the error message lines into an array list
			ArrayList<String> errorLines = new ArrayList<String>();
			String err_str = null;
			while ((err_str = processError.readLine()) != null) {
				if (!err_str.isEmpty()) {
					errorLines.add(err_str.trim());
					log.error("GF: " + err_str);
				}
			}
			
			//	/home/john/repositories/gf-eclipse-plugin/workspace-demo/Hello/ResEng.gf:5:17:
			//	   syntax error
			if (errorLines.get(0).matches(".+\\.gf:(\\d+):(\\d+):.*")) {
				// Don't worry about syntax errors, xtext will mark them for us
				return;
			}

			// Using XText marker type so that we get the tooltip on hover! Refer to: org.eclipse.xtext.ui.MarkerTypes
			IMarker marker = file.createMarker("org.eclipse.xtext.ui.check.normal"); // Instead of IMarker.PROBLEM
			marker.setAttribute(IMarker.USER_EDITABLE, false);
			marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
			marker.setAttribute(IMarker.LOCATION, file.getFullPath().toString());
			
			// Some errors are just a single error beginning with "gf:"
			if (errorLines.get(0).matches("^gf: (.+)$")) {
				marker.setAttribute(IMarker.MESSAGE, errorLines.get(0).substring(4));
				return;
			}
			
			//	File ParadXXigmsEng.gf does not exist.
			//	searched in: ./
            //				/home/john/repositories/gf-eclipse-plugin/workspace-demo/Functors
            //				/home/john/.cabal/share/gf-3.3/lib
            //				/home/john/.cabal/share/gf-3.3/lib/present
            //				/home/john/.cabal/share/gf-3.3/lib/prelude
			if (errorLines.get(0).matches("^File (.+) does not exist.$")) {
				marker.setAttribute(IMarker.MESSAGE, errorLines.get(0));
				return;
			}
			
			//	/home/john/repositories/gf-eclipse-plugin/workspace-demo/Hello/HelloEng.gf:9:
			//	Happened in the renaming of Recipient
			//	   constant not found: Gender
			//	   given ResEng, HelloEng
			Pattern pattern = Pattern.compile("([^/\\\\]+\\.gf):(\\d+)(-(\\d+))?:$");
			Matcher matcher = pattern.matcher(errorLines.get(0));
			if (matcher.find()) {
				Integer lineNo = Integer.parseInt(matcher.group(2));
//				Integer lineTo = Integer.parseInt(matcher.group(4));
				marker.setAttribute(IMarker.LINE_NUMBER, lineNo);

				// Set message, skipping first line
				StringBuilder sb = new StringBuilder();
				for (String s : errorLines.subList(1, errorLines.size())) {
					if (sb.length() > 0) sb.append("\n");
					sb.append(s);
				}
				marker.setAttribute(IMarker.MESSAGE, sb.toString());
			}
			
		} catch (IndexOutOfBoundsException e) {
			log.info("Unrecognized error format");
		} catch (NullPointerException e) {
			log.info("Unrecognized error format");
		} catch (CoreException e) {
			log.warn("Error creating marker on " + file.getName());
			e.printStackTrace();
		} catch (IOException e) {
			log.warn("Error parsing error stream for " + file.getName());
			e.printStackTrace();
		}		
	}

	
}

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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.grammaticalframework.eclipse.GFPreferences;
import org.grammaticalframework.eclipse.scoping.GFLibraryAgent;
import org.grammaticalframework.eclipse.scoping.TagEntry;
import org.grammaticalframework.eclipse.scoping.TagFileHelper;

import org.apache.log4j.Logger;

import com.google.inject.Inject;

/**
 * Custom GF builder, yeah!
 * Some refs..
 * 	http://wiki.eclipse.org/FAQ_How_do_I_implement_an_incremental_project_builder%3F
 * 	http://www.eclipse.org/articles/Article-Builders/builders.html
 * 
 * TODO Adding of markers to files
 * TODO Should this class actually be moved to the UI plugin?
 * TODO Support for monitor, when building takes a long time (progress, cancellation)
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
	 * Use tag based scoping?
	 */
	public static final Boolean TAG_BASED_SCOPING = true;

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
			// TODO Is doing a full build for every incremental change overkill?
			// Possible solution: only rebuild files whos tags contain something from the file being rebuilt
			if (TAG_BASED_SCOPING || kind == IncrementalProjectBuilder.FULL_BUILD) {
				fullBuild(monitor);
			} else {
				IResourceDelta delta = getDelta(getProject());
				if (delta == null) {
					fullBuild(monitor);
				} else {
					incrementalBuild(delta, monitor);
				}
			}
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
							cleanFile(file);
							if (buildFile(file)) {
								log.info("+ " + delta.getResource().getRawLocation());
							} else {
								log.warn("> Failed: " + delta.getResource().getRawLocation());
							}
							
							//TODO Update every other TAGS file to reflect this new info
//							propagateTagChanges(file);
						}
						
					}
					
					// Visit children too
					return true;
				}
			});
			
			// Force project refresh
			getProject().refreshLocal(IResource.DEPTH_INFINITE, monitor);
			
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	/**
	 * The library agent.
	 */
	@Inject
	private TagFileHelper tagHelper = new TagFileHelper();

	private void propagateTagChanges(IFile sourceFile) {
		
		// TODO What's the filepath format of tags on Windows machines?
		String sourceFilePath = sourceFile.getRawLocation().toOSString();
		String tagFilePath = getBuildDirectory(sourceFile) + "tags";
		
		Collection<TagEntry> owntags = (Collection<TagEntry>) tagHelper.getOwnTags(sourceFilePath, tagFilePath);		
		
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
					cleanFile((IFile) resource);
					if (buildFile((IFile) resource)) {
						log.info("+ " + resource.getName());
					} else {
						log.warn("> Failed: " + resource.getName());
					}
				}
			}
		});
		
		// Force project refresh
		getProject().refreshLocal(IResource.DEPTH_INFINITE, monitor);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.core.resources.IncrementalProjectBuilder#clean(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	protected void clean(final IProgressMonitor monitor) throws CoreException {
		log.info("Clean " + getProject().getName());
		
		// TODO Delete markers with getProject().deleteMarkers()
		recursiveDispatcher(getProject().members(), new CallableOnResource() {
			public void call(IResource resource) {
				// Check for cancellation
				if (monitor.isCanceled()) {
					throw new OperationCanceledException();
				}
				// Delete if necessary
				if (resource.getType() == IResource.FILE && resource.getFileExtension().equals("gfh")) {
					try {
						resource.delete(true, monitor);
						log.info("- " + resource.getName());
					} catch (CoreException e) {
						log.warn("> Failed: " + resource.getName());
						e.printStackTrace();
					}
				}
			}
		});
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
	 * @return true, if successful
	 */
	private boolean shouldBuild(IResource resource) {
		try {
			return resource.getType() == IResource.FILE && resource.getFileExtension().equals("gf") && !resource.getFullPath().toOSString().contains(BUILD_FOLDER);
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
	public static String getBuildSubfolderName(String sourceFileName) {
		return sourceFileName.substring(0, sourceFileName.lastIndexOf('.'));
	}
	private String getBuildDirectory(IFile file) {
		return getBuildDirectory(file, TAG_BASED_SCOPING);
	}
	private String getBuildDirectory(IFile file, boolean useIndividualFolders) {
		String filename = file.getName();
		if (useIndividualFolders) {
			return file.getRawLocation().removeLastSegments(1).toOSString()
					+ java.io.File.separator
					+ BUILD_FOLDER
					+ java.io.File.separator
					+ getBuildSubfolderName(filename)
					+ java.io.File.separator;
		} else {
			return file.getRawLocation().removeLastSegments(1).toOSString()
				+ java.io.File.separator
				+ BUILD_FOLDER
				+ java.io.File.separator;
		}
	}
	
	private boolean buildFile(IFile file) {
		if (TAG_BASED_SCOPING)
			return buildFileTAGS(file);
		else
			return buildFileSS(file);
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
		String buildDir = getBuildDirectory(file);

		try {
			// Check the build directory and try to create it
			File buildDirFile = new File(buildDir);
			if (!buildDirFile.exists()) {
				buildDirFile.mkdir();
			}

			// Create the scraped version
			createScrapedFileCopy(".."+java.io.File.separator+".."+java.io.File.separator+filename, filename, buildDirFile);

			// TODO TEMP: Run --batch first to make it happy
			ProcessBuilder pbBatch = new ProcessBuilder(gfPath, "--batch", "--path=.."+java.io.File.separator+".."+java.io.File.separator, filename);
			pbBatch.directory(buildDirFile);
			pbBatch.redirectErrorStream(true);
			Process procBatch = pbBatch.start();
			procBatch.waitFor();
			
			// Compile to get tags with: gf --tags --path=../../ HelloEng.gf
			ArrayList<String> command = new ArrayList<String>();
			command.add(gfPath);
			command.add("--tags");
			command.add("--path=.."+java.io.File.separator+".."+java.io.File.separator);
			if (gfLibPath != null && !gfLibPath.isEmpty()) {
				command.add(String.format("--gf-lib-path=\"%s\"", gfLibPath)); // Use library path in command (if supplied)
			}
			command.add(filename); // Compile the version in the BUILD folder

			// Execute command
			ProcessBuilder pbTags = new ProcessBuilder(command);
			pbTags.directory(buildDirFile);
			pbTags.redirectErrorStream(true);
			Process procTags = pbTags.start();
			
			// Consume & log all output
			BufferedReader processOutput = new BufferedReader(new InputStreamReader(procTags.getInputStream()));
			String out_str;
			while ((out_str = processOutput.readLine()) != null) {
				log.debug("GF: " + out_str);
			}
			
			// Tidy up
			processOutput.close();
			procTags.waitFor();
			int retVal = procTags.exitValue();
			return (retVal == 0);
			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	/**
	 * Create a copy of a source file with only the module header intact, and the body "scraped out".
	 * The reasons for this is to be able to get a valid tags file even when there are syntax/type/ref errors
	 * in the current source file.
	 * 
	 * @param sourceFileName
	 * @param targetFileName
	 * @param workingDirectory
	 * @return
	 */
	private boolean createScrapedFileCopy(String sourceFileName, String targetFileName, File workingDirectory) {
		try {
			// MAJOR TODO: use of absolute path!! Reliance on sed!!one!
			ProcessBuilder b = new ProcessBuilder("/bin/sed", "-n", "1h;1!H;${;g;s/{.*/{}/g;p;}", sourceFileName);
			b.directory(workingDirectory);
			Process process = b.start();
			
			// Consume output and write to targetFile
			File targetFile = new File(workingDirectory.getAbsolutePath() + java.io.File.separator + targetFileName);
			BufferedWriter writer = new BufferedWriter(new FileWriter(targetFile));
			BufferedReader processOutput = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String out_str;
			while ((out_str = processOutput.readLine()) != null) {
				writer.write(out_str + "\n");
			}
			
			process.waitFor();
			writer.close();
			processOutput.close();
			int retVal = process.exitValue();
			return (retVal == 0);
		} catch (IOException e) {
			log.error("Couldn't create scraped version of " + sourceFileName);
		} catch (InterruptedException e) {
			log.error("Scraping of " + sourceFileName + " interrupted");
		}
		return false;				
	}
	
	
	/**
	 * For a single .gf file, compile it with GF and run "ss -strip -save" to
	 * capture all the GF headers in the build subfolder.
	 * 
	 * TODO Share a single process for the whole build cycle to save on overheads
	 *
	 * @param file the file
	 * @return true, if successful
	 */
	private boolean buildFileSS(IFile file) {
		/* 
		 * We want to compile each source file in .gf with these commands:
		 * i --retain HelloEng.gf
		 * ss -strip -save
		 * 
		 * Shell command: echo "ss -strip -save" | gf -retain HelloEng.gf
		 */
		String filename = file.getName();
		String buildDir = getBuildDirectory(file);
		
		ArrayList<String> command = new ArrayList<String>();
		command.add(gfPath);
		command.add("--retain");
		
		// Use library path in command (if supplied)
		if (gfLibPath != null && !gfLibPath.isEmpty()) {
			command.add(String.format("--gf-lib-path=\"%s\"", gfLibPath));
		}
		
		command.add(".." + java.io.File.separator + filename);
		
		try {
			// Check the build directory and try to create it
			File buildDirFile = new File(buildDir);
			if (!buildDirFile.exists()) {
				buildDirFile.mkdir();
			}
			
			// Piece together our GF process
			ProcessBuilder b = new ProcessBuilder(command);
			b.directory(buildDirFile);
			b.redirectErrorStream(true);
			Process process = b.start();
			
			// Feed it our commands, then quit
			BufferedWriter processInput = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
			processInput.write("ss -strip -save");
			processInput.newLine();
			processInput.flush();
			processInput.write("quit");
			processInput.newLine();
			processInput.flush();
			
			// Consume & log all output
			BufferedReader processOutput = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String out_str;
			while ((out_str = processOutput.readLine()) != null) {
				log.debug("GF: " + out_str);
			}
			
			// Tidy up
			processInput.close();
			processOutput.close();
			process.waitFor();
			return true;
			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		return false;		
	}
	
	/**
	 * Clean all the files in the build directory for a given file.
	 *
	 * @param file the file
	 */
	private void cleanFile(IFile file) {
		if (TAG_BASED_SCOPING) {
			log.info("Cleaning build directory for " + file.getName());
			String buildDir = getBuildDirectory(file);
			// Check the build directory and delete all its contents
			File buildDirFile = new File(buildDir);
			if (buildDirFile.exists()) {
				File[] files = buildDirFile.listFiles();
				for (File f : files) {
					try {
						f.delete();
						log.info("- " + f.getName());
					} catch (Exception _) {
						log.warn("> Failed: " + f.getName());
					}
				}
			}
		}
	}
	
}

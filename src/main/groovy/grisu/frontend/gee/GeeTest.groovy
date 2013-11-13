package grisu.frontend.gee

import grisu.control.JobnameHelpers
import grisu.control.ServiceInterface
import grisu.frontend.control.GJob
import grisu.frontend.model.job.GrisuJob
import grisu.model.FileManager
import grisu.model.GrisuRegistryManager

import java.text.DateFormat

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.google.common.collect.Lists


class GeeTest {

	public static Logger myLogger = LoggerFactory.getLogger(GeeTest.class)

	private static getTestName(def folder) {
		if ( folder instanceof File ) {
			return folder.getName()
		} else {
			return FileManager.getFilename(folder)
		}
	}

	public final static plainFormatter = DateFormat.instance

	private final File folder
	private final File files_folder
	private final File checks_folder
	private final File job_folder
	private final File submit_properties_file
	private final File checks_properties_file

	private final File log_folder
	private final File log_archive_folder
	private final File log_failed_folder
	private final File log_file

	private final File root_folder
	private final File global_checks_folder

	private final String application_name

	public final String test_name
	public final String jobname

	private File stdout_file
	private File stderr_file

	private final GrisuJob job;

	public boolean success = true
	private def failed_checks = []

	private final ServiceInterface si;
	private final FileManager fm;

	private final GJob gJob;


	/**
	 * Use this constructor to create a new (test-)job
	 * @param folder the test folder
	 */
	public GeeTest(def folder, def logs_folder) {
		this("gee_"+JobnameHelpers.calculateTimestampedJobname(getTestName(folder)), folder, logs_folder)
		//submitJob()
	}


	private GeeTest(def jobname, def folder, def log_folder) {

		this.test_name = getTestName(folder)

		if ( folder instanceof File ) {
			this.folder = folder
		} else {
			this.folder = new File(folder)
		}

		this.jobname = jobname

		if ( ! Gee.TESTS_DIR_NAME.equals(this.folder.getParentFile().getName() ) ) {
			println "Parent folder of test '"+this.test_name+ "' not called Tests. Exiting..."
			System.exit(1)
		}

		this.si = GrisuRegistryManager.getDefaultServiceInterface()
		this.fm = GrisuRegistryManager.getFileManager(this.si);

		this.application_name = this.folder.getParentFile().getParentFile().getName()

		this.root_folder = this.folder.getParentFile().getParentFile().getParentFile()
		this.global_checks_folder = new File(this.root_folder, Gee.CHECKS_DIR_NAME)

		this.submit_properties_file = new File(this.folder, Gee.TEST_PROPERTIES_FILE_NAME)

		try {
			gJob = new GJob(this.submit_properties_file, this.jobname)
		} catch (Exception e) {
			println "Can't create job from '"+this.submit_properties_file+"': "+e.getLocalizedMessage()
			System.exit(1)
		}
		job = gJob.createJobDescription(si)

		this.checks_properties_file = new File(this.folder, Gee.CHECKS_PROPERTIES_FILE_NAME)
		this.checks_folder = new File(this.folder, Gee.CHECKS_DIR_NAME)
		if ( ! log_folder ) {
			this.log_folder = new File(this.root_folder, Gee.LOG_FOLDER_NAME+File.separator+this.application_name)
		} else {
			this.log_folder = log_folder
		}

		if ( ! this.log_folder.mkdirs() && ! this.log_folder.exists() ) {
			println ("Could not create folder for logs: "+this.log_folder.getAbsolutePath())
			System.exit(1)
		}

		this.log_archive_folder = new File(this.log_folder, Gee.LOG_ARCHIVE_FOLDER_NAME+File.separator+this.application_name)
		if ( ! this.log_archive_folder.mkdirs() && ! this.log_archive_folder.exists() ) {
			println ("Could not create folder for archived logs: "+this.log_archive_folder.getAbsolutePath())
			System.exit(1)
		}

		this.log_failed_folder = new File(this.log_folder, Gee.LOG_FAILED_FOLDER_NAME+File.separator+this.application_name+File.separator+this.jobname)


		this.log_file = new File(this.log_folder, this.jobname+".log")
	}

	private synchronized void addLogMessage(String msg) {

		String tmp = plainFormatter.format(new Date()) + " - " +msg + "\n"

		this.log_file.append(tmp)

		if ( Gee.verbose ) {
			println "["+jobname+"] "+msg
		}

		myLogger.debug(msg)
	}



	public void submitJob() {

		try {

			addLogMessage("Jobname: "+getJob().getJobname())
			addLogMessage("Submitting job...")
			getJob().submitJob()
			addLogMessage("Job submitted")
		} catch (Exception e) {
			e.printStackTrace()
			addLogMessage("Could not submit job for this test: "+e.getLocalizedMessage())
			success = false
		}
	}


	public void waitForJobToFinish(int waittime) {
		if ( success ) {
			addLogMessage("Waiting for job to finish...")
			getJob().waitForJobToFinish(waittime)
			addLogMessage("Job finished")
		}
	}

	public void runChecks() {

		if ( success ) {

			try {
				addLogMessage("Running checks...")

				this.checks_properties_file.eachLine { line ->
					line = line.trim()
					if ( ! line || line.startsWith("#")) {
						return
					}

					def checkname = null
					def index = line.indexOf("=")
					def zero_expected = true

					if ( index > 0 ) {
						def space_index = line.indexOf(" ")
						// only assume it's a named check if there is no whitespace before the = char
						if ( index < space_index ) {
							checkname = line.substring(0, index)
							line = line.substring(index+1)
							line = line.trim()
						}
					}

					if ( line.startsWith("!") ) {
						zero_expected = false
						line = line.substring(1)
						line = line.trim()
					}

					def tokens = line.tokenize()
					def exe = tokens[0]

					if ( ! checkname ) {
						checkname = exe
					}


					def cmd = Lists.newLinkedList()

					// finding executable
					// try in 'checks' folder
					File exe_file = new File(checks_folder, exe)
					if ( exe_file.exists() ) {
						cmd.add(exe_file.getAbsolutePath())
					} else {
						exe_file = new File(global_checks_folder, exe)
						if ( exe_file.exists() ) {
							cmd.add(exe_file.getAbsolutePath())
						} else {
							cmd.add(exe)
						}
					}

					// parsing tokens
					for ( String t : tokens.subList(1, tokens.size()) ) {

						// find special markers
						def marker_index = t.indexOf("(")
						if ( marker_index < 0 ) {
							cmd.add(t)
							continue
						}

						String marker = t.substring(0, marker_index)
						String tok = t.substring(marker_index+1, t.lastIndexOf(")"))

						if ( ! marker || ! tok ) {
							addLogMessage "Invalid syntax: "+marker
							println "Checks '"+checks_properties_file.getAbsolutePath()+"': Invalid syntax '"+marker+"'"
							System.exit(1)
						}

						switch (marker) {
							case 'file':
								addLogMessage "Downloading: "+tok
								def file = getJob().downloadAndCacheOutputFile(tok)
								cmd.add(file.getAbsolutePath())
								break
							case 'filesize':
								addLogMessage 'Getting filesize: '+tok
								def filesize = getJob().getFileSize(tok)
								cmd.add(filesize.toString())
								break
							case 'exists':
								addLogMessage 'Checking whether the file exists: '+getJob().getJobDirectoryUrl()+"/"+tok
								def exists = fm.fileExists(getJob().getJobDirectoryUrl()+"/"+tok)
								cmd.add(exists.toString())
								break
							case 'content':
								addLogMessage 'Getting content of file: '+tok
								def content = getJob().getFileContent(tok)
								cmd.add(content)
								break;
							//					case 'check_dir':
							//						addLogMessage 'Finding file in check directory: '+tok
							//						def path = folder.getAbsolutePath() + File.separator + tok
							//						cmd.add(path)
							//						break;
							default:
								addLogMessage 'Not a valid marker: '+marker
								println "Checks '"+checks_properties_file.getAbsolutePath()+"': Invalid syntax '"+marker+"'"
								System.exit(1)
						}

					}
					executeCheck(checkname, cmd, zero_expected)

				}

				addLogMessage("All checks finished")

			} catch (Exception ex) {
				ex.printStackTrace()
			}

		}

		cleanup()

	}

	private void executeCheck(def check_name, def cmd, def zero_expected) {

		addLogMessage "Running check: "+check_name
		addLogMessage "Check details: "+cmd

		Process proc = null

		ProcessBuilder procBuilder = new ProcessBuilder(cmd)
		procBuilder.directory(folder)

		def env = procBuilder.environment()
		getJob().getAllJobProperties().each { key, value ->
			env['JOB_'+key] = value
		}
		env['JOB_exit_code'] = (getJob().getStatus(false)-1000).toString()

		proc = procBuilder.start()

		proc.waitFor()


		def stdout = []
		proc.in.text.split('\n').each { it ->
			def temp = it.trim()
			if ( temp ) {
				stdout.add(it.trim())
			}
		}
		def stderr = []
		proc.err.text.split('\n').each { it ->
			def temp = it.trim()
			if ( temp ) {
				stderr.add(temp)
			}
		}

		int exit = proc.exitValue()

		if ( (exit == 0 && zero_expected) || (exit != 0 && ! zero_expected) ) {
			addLogMessage ("Check finished, all good. Exit code: "+exit)
		} else {
			failed_checks.add(check_name)
			addLogMessage ("Check finished, wrong exit code ( "+exit+" ), check failed")

			if ( ! this.log_failed_folder.mkdirs() && ! this.log_failed_folder.exists() ) {
				println ("Could not create folder for failed logs: "+this.log_failed_folder.getAbsolutePath())
				System.exit(1)
			}

			String check_file = check_name+".stdout"
			File stdout_fail = new File(log_failed_folder, check_file)
			stdout.each {
				stdout_fail.append(it+"\n")
			}
			check_file = check_name+".stderr"
			File stderr_fail = new File(log_failed_folder, check_file)
			stderr.each{
				stderr_fail.append(it+"\n")
			}
			success = false
		}
	}


	public List<String> getFailedChecks() {
		return failed_checks
	}

	public boolean isSuccess() {
		return success
	}

	private void cleanup() {

		if ( success ) {

			addLogMessage("Cleaning job: "+getJob().getJobname())
			getJob().kill(true)
			addLogMessage("Job cleaned.")
			log_file.renameTo(new File(log_archive_folder, log_file.getName()));
		} else {
			addLogMessage("Job failed, not cleaning it.")
			log_file.renameTo(new File(log_failed_folder, log_file.getName()));
		}

	}

	public GrisuJob getJob() {
		return this.job
	}

}

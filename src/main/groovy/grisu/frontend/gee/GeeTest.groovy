package grisu.frontend.gee

import grisu.control.ServiceInterface
import grisu.frontend.model.job.JobObject
import grisu.model.FileManager
import grisu.model.GrisuRegistryManager
import grisu.model.job.JobDescription

import java.text.DateFormat

import org.python.google.common.collect.Lists


class GeeTest {
	
	public final static plainFormatter = DateFormat.instance

	public final static String TESTS_DIR_NAME = "tests"
	public final static String CHECKS_DIR_NAME = "checks"
	public final static String JOB_PROPERTIES_FILE_NAME = "job.config"
	public final static String CHECKS_PROPERTIES_FILE_NAME = "checks.config"

	private final File folder
	private final File files_folder
	private final File checks_folder
	private final File job_properties_file
	private final File checks_properties_file

	private File stdout_file
	private File stderr_file

	private JobObject job;

	private final ServiceInterface si;
	private final FileManager fm;

	public GeeTest(def folder) {

		this.si = GrisuRegistryManager.getDefaultServiceInterface()
		this.fm = GrisuRegistryManager.getFileManager(this.si);

		if ( folder instanceof File ) {
			this.folder = folder
		}
		this.folder = new File(folder)

		this.files_folder = new File(this.folder, TESTS_DIR_NAME)

		this.job_properties_file = new File(this.folder, JOB_PROPERTIES_FILE_NAME)

		this.checks_properties_file = new File(this.folder, CHECKS_PROPERTIES_FILE_NAME)

		this.checks_folder = new File(this.folder, CHECKS_DIR_NAME)
		
	}
	
	private void addLogMessage(String msg) {
		
		println plainFormatter.format(new Date()) + " - " +msg
		
	}

	private void createJob() {

		def config = new ConfigSlurper().parse(job_properties_file.toURI().toURL()).flatten()

		JobDescription jd = new JobDescription(config)

		job = JobObject.createJobObject(si, jd)
		//		job.setApplication('generic')
		job.setSubmissionLocation('pan:pan.nesi.org.nz')
	}

	public void submitJob() {
		job.createJob('/nz/nesi')
		job.submitJob()
	}

	
	public void waitForJobToFinish(int waittime) {
		getJob().waitForJobToFinish(waittime)
	}

	public void runChecks() {

		this.checks_properties_file.eachLine { line ->
			line = line.trim()
			if ( ! line || line.startsWith("#")) {
				return
			}

			def tokens = line.tokenize()
			def exe = tokens[0]


			def cmd = Lists.newLinkedList()

			// finding executable
			File exe_file = new File(checks_folder, exe)
			if ( exe_file.exists() ) {
				cmd.add(exe_file.getAbsolutePath())
			} else {
				cmd.add(exe)
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

				if ( marker > tok ) {
					addLogMessage "Invalid syntax around: "+marker
					System.exit(1)
				}

				switch (marker) {
					case 'file':
						addLogMessage "Downloading: "+tok
						def file = job.downloadAndCacheOutputFile(tok)
						cmd.add(file.getAbsolutePath())
						break
					case 'filesize':
						addLogMessage 'Getting filesize: '+tok
						def filesize = job.getFileSize(tok)
						cmd.add(filesize.toString())
						break
					case 'exists':
						addLogMessage 'Checking whether the file exists: '+job.getJobDirectoryUrl()+"/"+tok
						def exists = fm.fileExists(job.getJobDirectoryUrl()+"/"+tok)
						cmd.add(exists.toString())
						break
					case 'content':
						addLogMessage 'Getting content of file: '+tok
						def content = job.getFileContent(tok)
						cmd.add(content)
						break;
					default:
						addLogMessage 'Not a valid marker: '+marker
						System.exit(1)
				}

			}

			executeCheck(cmd)

		}

		//		this.checks_folder.eachFileMatch ( ~/check.*/ )  { file ->
		//			executeCheck(file.getAbsolutePath())
		//		}


	}

	private void executeCheck(def cmd) {

		
		addLogMessage "Running check: "+cmd

		Process proc = null

		ProcessBuilder procBuilder = new ProcessBuilder(cmd)

		def env = procBuilder.environment()
		job.getAllJobProperties().each { key, value ->
			env['JOB_'+key] = value
		}
		env['JOB_exit_code'] = (job.getStatus(false)-1000).toString()

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

		println "STDOUT: "
		for ( def s : stdout ) {
			println "\t"+s
		}
		println "STDERR: "
		for ( def s : stderr ) {
			println "\t"+s
		}
		println "EXITCODE: "+exit
	}

	public JobObject getJob() {
		return this.job
	}

}

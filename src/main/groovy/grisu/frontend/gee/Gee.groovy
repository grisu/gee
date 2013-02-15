package grisu.frontend.gee

import grisu.frontend.GeeCliParameters
import grisu.frontend.control.login.LoginManager
import grisu.frontend.view.cli.GrisuCliClient
import grisu.jcommons.constants.Constants
import grisu.jcommons.constants.JobSubmissionProperty;
import grisu.jcommons.utils.PackageFileHelper;
import grisu.jcommons.view.html.VelocityUtils
import groovy.io.FileType

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import com.google.common.collect.Maps

class Gee extends GrisuCliClient<GeeCliParameters> {

	public static boolean verbose = false

	public final static String GROUP_KEY = "group"
	public final static String QUEUE_KEY = "queue"
	public final static String JOBNAME_KEY = Constants.JOBNAME_KEY

	public final static String TESTS_DIR_NAME = "tests"
	public final static String JOBS_DIR_NAME = "jobs"
	public final static String CHECKS_DIR_NAME = "scripts"
	public final static String CHECKS_PROPERTIES_FILE_NAME = "checks.config"
	public final static String TEST_PROPERTIES_FILE_NAME = "test.config"
	

	public final static String LOG_FOLDER_NAME = "logs"
	public final static String LOG_ARCHIVE_FOLDER_NAME = "archived"
	public final static String LOG_FAILED_FOLDER_NAME = "failed"


	static Map<String, String> parsePropertiesFile(def file, def config_filename = null) {

		if ( config_filename ) {
			file = getConfigFile(file, config_filename)
		}

		if ( file instanceof String ) {
			file = new File(file)
		}
		Properties props = new Properties();
		FileInputStream fis = new FileInputStream(file);

		//loading properites from properties file
		props.load(fis);

		return props

	}

	public static File getConfigFile(def file_or_folder, String config_filename) {

		if (file_or_folder instanceof String) {
			file_or_folder = new File(file_or_folder)
		}

		File result = null

		if ( file_or_folder instanceof File ) {
			if ( file_or_folder.isFile() && config_filename.equals(file_or_folder.getName()) ) {
				result = file_or_folder
			} else if ( file_or_folder.isDirectory() ) {
				result = new File(file_or_folder, config_filename)
			} else {
				throw new RuntimeException("Can't figure out jobname for: "+file_or_folder)
			}
		} else {
			throw new RuntimeException("Can't get submit config file: "+file_or_folder)
		}

		if ( ! result.exists() ) {
			throw new RuntimeException("Config file: "+result.getAbsolutePath()+" does not exist.")
		}
		return result
	}

	static main(args) {

		LoginManager.initGrisuClient("gee");

		// helps to parse commandline arguments, if you don't want to create
		// your own parameter class, just use DefaultCliParameters
		GeeCliParameters params = new GeeCliParameters();

		// create the client
		Gee gee = null;
		try {
			gee = new Gee(params, args);
		} catch(Exception e) {
			System.err.println("Could not start gee: "
					+ e.getLocalizedMessage());
			System.exit(1);
		}

		// finally:
		// execute the "run" method below
		gee.run();

		// exit properly
		System.exit(0);

	}


	public Gee(GeeCliParameters params, String[] args) throws Exception {
		super(params, args)
		if ( getCliParameters().isVerbose() ) {
			Gee.verbose = true
		}

		if ( ! getCliParameters().isCreate_test_stub() ) {
			getServiceInterface()
		}
	}

	private static void createTestStub(File path, String app, String name) {

		File test_folder = new File(path.getAbsolutePath()+File.separator+app+File.separator+TESTS_DIR_NAME+File.separator+name)
		File job_folder = new File(path.getAbsolutePath()+File.separator+app+File.separator+JOBS_DIR_NAME+File.separator+name)

		if (test_folder.exists()) {
			println ("Test directory "+test_folder.getAbsolutePath()+" already exists, not creating test stub.")
			System.exit(1)
		}

		test_folder.mkdirs()

		if (!test_folder.exists()) {
			println ("Can't create directory "+test_folder.getAbsolutePath()+".")
			System.exit(1)
		}

		if (job_folder.exists()) {
			println("Jobs folder already exists, not creating new one.")
		} else {
			try {
				GJob.createJobStub(job_folder, name)
			} catch (Exception e) {
				println("Can't create job: "+job_folder.getAbsolutePath()+".")
				System.exit(1)
			}
		}

		File temp = null

		temp = PackageFileHelper.getFile('checks.config')
		new File(test_folder, CHECKS_PROPERTIES_FILE_NAME) << temp.text

		Map properties = Maps.newHashMap()
		String relativePath = "../../"+JOBS_DIR_NAME+"/"+job_folder.getName()+"/"+GJob.JOB_PROPERTIES_FILE_NAME

		properties.put('job_dir', relativePath)
		String configContent = VelocityUtils.render('test.config', properties)
		new File(test_folder, TEST_PROPERTIES_FILE_NAME) << configContent

		File checks = new File(test_folder, CHECKS_DIR_NAME)
		checks.mkdirs()
		if ( ! checks.exists() ) {
			println ("Can't create folder: "+checks.getAbsolutePath())
			System.exit(1)
		}

		temp = PackageFileHelper.getFile('readme_checks.txt')
		new File(checks, 'readme.txt') << temp.text

		temp = PackageFileHelper.getFile('check_for_some_string.py')
		File example_script = new File(checks, 'check_for_some_string.py')
		example_script << temp.text

		example_script.setExecutable(true)



		File global_checks = new File(path, CHECKS_DIR_NAME)
		if ( ! global_checks.exists() ) {
			global_checks.mkdirs()
			if ( ! global_checks.exists() ) {
				println "Can't create folder: "+global_checks.getAbsolutePath()
				System.exit(1)
			}
		}

		temp = PackageFileHelper.getFile('is_true.py')


		File isTrue = new File(global_checks, 'is_true.py')
		if ( ! isTrue.exists() ) {
			isTrue << temp.text

			isTrue.setExecutable(true)
		}

		temp = PackageFileHelper.getFile('is_not_true.py')

		File isNotTrue = new File(global_checks, 'is_not_true.py')
		if ( ! isNotTrue.exists() ) {
			isNotTrue << temp.text

			isNotTrue.setExecutable(true)
		}

	}

	@Override
	public void run() {

		def root_folder
		def logs_folder

		def job_files = []

		String root_folder_path = getCliParameters().getFolder()
		String testpath = getCliParameters().getTest()

		if ( ! root_folder_path && ! testpath ) {
			println "No application folder or test specified. Use the -f/--application-folder or -t/--test option."
			System.exit(1)
		}

		if ( testpath ) {
			job_files = [testpath]
		} else {

			root_folder = new File(root_folder_path)

			if ( ! root_folder.exists() ) {
				root_folder.mkdirs()
				if ( ! root_folder.exists() ) {
					println "Could not create folder: "+root_folder_path
					System.exit(1)
				}
			}

			logs_folder = getCliParameters().getLogsFolder()

			if ( ! logs_folder ) {
				logs_folder = new File(root_folder, LOG_FOLDER_NAME)
			} else {
				logs_folder = new File(logs_folder)
			}

			logs_folder.mkdirs()
			if (! logs_folder.exists() ) {
				println "Can't create folder for logs: "+logs_folder
				System.exit(1)
			}

			String appName = getCliParameters().getApp()
			String testName = getCliParameters().getTestName()


			if ( getCliParameters().isCreate_test_stub() ) {
				if ( ! appName ) {
					println "No application name specified, can't create test stub..."
					System.exit(1)
				}
				if ( ! testName ) {
					println "No testname specified, can't create test stub..."
				}

				println "Creating test stub for application "+appName+": "+testName
				createTestStub(root_folder, appName, testName)
				println "Stub created."

				System.exit(0)
			}

			// figuring out which tests to run
			root_folder.traverse(type: FileType.FILES, nameFilter: ~/test.config$/) { it -> job_files << it }

			if ( testName ) {
				job_files = job_files.findAll { it ->
					it.getAbsolutePath().contains(File.separator+TESTS_DIR_NAME+File.separator+testName)
				}
			}

			if ( appName ) {
				job_files = job_files.findAll() { it ->
					it.getAbsolutePath().contains(File.separator+appName+File.separator+TESTS_DIR_NAME)
				}
			}



			if ( job_files.size() == 0 ) {
				println "No test job configs found."
				System.exit(1)
			}
		}

		submit(job_files, logs_folder)

	}

	private void submit(def job_files, def logs_folder) {

		def THREADS = 10
		ExecutorService pool = Executors.newFixedThreadPool(THREADS)

		println("Running tests:")
		job_files.each { println it }

		println ""

		def tests = [].asSynchronized()

		job_files.each { it ->

			Thread t = new Thread() {

						public void run() {
							println 'Creating test using config file: '+it
							GeeTest test = new GeeTest(it.getParentFile(), logs_folder)
							tests.add(test)
							test.submitJob()
						}
					}

			pool.execute(t)
		}

		println "Waiting for submissions to finish..."

		pool.shutdown()
		pool.awaitTermination(10, TimeUnit.HOURS)

		println "Submissions finished."

		pool = Executors.newFixedThreadPool(THREADS)

		int exitCode = 0

		tests.each { test ->
			Thread t = new Thread() {
						public void run() {
							println "Waiting for test to finish: "+test.test_name
							test.waitForJobToFinish(4)
							if ( ! test.success ) {
								println "Job not submitted or failed, not running checks..."
							} else {
								println "Job finished for test: "+test.test_name+". Running checks..."
								test.runChecks()
							}
						}
					}
			pool.execute(t)
		}

		println "Waiting for jobs to finish..."

		pool.shutdown()
		pool.awaitTermination(24, TimeUnit.HOURS)

		println "Checks finished."

		if ( tests.any{ it ->
			! it.isSuccess()
		}) {
			println "At least one test failed: "
			def failed_jobs = tests.findAll { ! it.isSuccess() }

			failed_jobs.each { test ->
				println "Test: "+test.test_name
				test.getFailedChecks().each { checkName -> println '\t'+checkName }
			}

			exitCode = 1

		} else {

			println "All good! No check failed. Hm, that can't be right..."

		}


		System.exit(exitCode)
	}

}

package grisu.frontend.gee

import grisu.frontend.GeeCliParameters
import grisu.frontend.control.login.LoginManager
import grisu.frontend.utils.PackageFileHelper
import grisu.frontend.view.cli.GrisuCliClient
import groovy.io.FileType

class Gee extends GrisuCliClient<GeeCliParameters> {

	public final static String TESTS_DIR_NAME = "tests"
	public final static String CHECKS_DIR_NAME = "checks"
	public final static String FILES_DIR_NAME = "files"
	public final static String JOB_PROPERTIES_FILE_NAME = "job.config"
	public final static String CHECKS_PROPERTIES_FILE_NAME = "checks.config"

	public final static String LOG_FOLDER_NAME = "logs"
	public final static String LOG_ARCHIVE_FOLDER_NAME = "archived"
	public final static String LOG_FAILED_FOLDER_NAME = "failed"

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
		getServiceInterface()
	}

	private static void createTestStub(File path, String app, String name) {
		
		File folder = new File(path.getAbsolutePath()+File.separator+app+File.separator+TESTS_DIR_NAME+File.separator+name)

		if (folder.exists()) {
			println ("Test directory "+folder.getAbsolutePath()+" already exists, not creating test stub.")
			System.exit(1)
		}

		folder.mkdirs()

		if (!folder.exists()) {
			println ("Can't create directory "+folder.getAbsolutePath()+".")
			System.exit(1)
		}

		File temp = PackageFileHelper.getFile('job.config')
		new File(folder, JOB_PROPERTIES_FILE_NAME) << temp.text

		temp = PackageFileHelper.getFile('checks.config')
		new File(folder, CHECKS_PROPERTIES_FILE_NAME) << temp.text

		temp = new File(folder, FILES_DIR_NAME)
		temp.mkdirs()
		if ( ! temp.exists() ) {
			println ("Can't create folder: "+temp.getAbsolutePath())
			System.exit(1)
		}

		File checks = new File(folder, CHECKS_DIR_NAME)
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

		File input_files = new File(folder, FILES_DIR_NAME)
		temp = PackageFileHelper.getFile('example_input_file.txt')
		new File(input_files, 'example_input_file.txt') << temp.text

		temp = PackageFileHelper.getFile('readme_files.txt')
		new File(folder, 'readme.txt') << temp.text
		
		
		
		File global_checks = new File(path, CHECKS_DIR_NAME)
		if ( ! global_checks.exists() ) {
			global_checks.mkdirs()
			if ( ! global_checks.exists() ) {
				println "Can't create folder: "+global_checks.getAbsolutePath()
				System.exit(1)
			}
		}
		
		temp = PackageFileHelper.getFile('is_true.py')
		
		if ( ! temp.exists() ) {
		
			File isTrue = new File(global_checks, 'is_true.py')
			isTrue << temp.text
		
			isTrue.setExecutable(true)
		}
		
		temp = PackageFileHelper.getFile('is_not_true.py')
		
		if ( ! temp.exists() ) {
			File isNotTrue = new File(global_checks, 'is_not_true.py')
			isNotTrue << temp.text
		
			isNotTrue.setExecutable(true)
		}

	}

	@Override
	public void run() {


		def job_files = []

		String root_folder_path = getCliParameters().getFolder()

		if ( ! root_folder_path ) {
			println "No application folder specified. Use the -f/--application-folder option."
			System.exit(1)
		}

		File root_folder = new File(root_folder_path)

		if ( ! root_folder.exists() ) {
			root_folder.mkdirs()
			if ( ! root_folder.exists() ) {
				println "Could not create folder: "+root_folder_path
				System.exit(1)
			}
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
		root_folder.traverse(type: FileType.FILES, nameFilter: ~/job.config$/) { it -> job_files << it }

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

		println("Running tests:")
		job_files.each { println it }

		println ""

		def tests = []

		job_files.each { it ->

			println 'Creating test using config file: '+it

			GeeTest test = new GeeTest(it.getParentFile())

			tests.add(test)
		}

		tests.each { test ->
			test.waitForJobToFinish(4)
			test.runChecks()
		}

		if ( tests.any{ it ->
			! it.isSuccess()
		}) {
			println "At least one check failed: "
			def failed_jobs = tests.findAll { ! it.isSuccess() }

			failed_jobs.each { test ->
				println "Test: "+test.test_name
				test.getFailedChecks().each { checkName -> println '\t'+checkName }
			}
		} else {

			println "All good! No check failed. Hm, that can't be right..."

		}




	}

}

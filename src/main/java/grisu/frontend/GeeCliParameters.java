package grisu.frontend;

import grisu.frontend.view.cli.GrisuCliParameters;
import grisu.jcommons.constants.Constants;

import com.beust.jcommander.Parameter;

public class GeeCliParameters extends GrisuCliParameters {

	@Parameter(names = { "-f", "--applications-folder" }, description = "root folder containing the applications (default: current folder)")
	private String folder = System.getProperty("user.dir");
	
	@Parameter(names = {"--reset" }, description = "move all failed job log fails into the archive folder")
	private boolean reset;
	
	@Parameter(names = {"--create-test-stub" }, description = "create a new test client stub")
	private boolean create_test_stub;
	
	@Parameter(names = {"--package", "-p" }, description = "the package name")
	private String app;
	
	@Parameter(names = {"--testname" }, description = "the test name")
	private String testname;
	
	@Parameter(names = {"-v", "--verbose"}, description = "more debug output on stdout")
	private boolean verbose;
	
	@Parameter(names = {"--logs"}, description = "the location where logs are kept (default: 'logs' subfolder of applications folder)")
	private String logsFolder;
	
	@Parameter(names = {"--test", "-t"}, description = "the folder where the test to execute is located")
	private String test;
	
	public String getTest() {
		return test;
	}
	
	public String getLogsFolder() {
		return logsFolder;
	}
	
	public boolean isVerbose() {
		return verbose;
	}

	public boolean isReset() {
		return reset;
	}

	public boolean isCreate_test_stub() {
		return create_test_stub;
	}

	public String getApp() {
		return app;
	}

	public String getFolder() {
		return folder;
	}
	
	public String getTestName() {
		return testname;
	}

}

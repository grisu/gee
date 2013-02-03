package grisu.frontend.gee

import grisu.control.ServiceInterface
import grisu.frontend.GeeCliParameters
import grisu.frontend.control.login.LoginManager
import grisu.frontend.model.job.JobObject
import grisu.frontend.view.cli.GrisuCliClient
import grisu.model.job.JobDescription

class Gee extends GrisuCliClient<GeeCliParameters> {
	
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

	@Override
	public void run() {

		GeeTest test = new GeeTest("/home/markus/src/grisu/gee/examples/R/tests/R_version/")
		
		test.createJob()
		
		test.submitJob()
		
		test.waitForJobToFinish(4)
		
		test.runChecks()

	}

}

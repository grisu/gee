Gee
===

Gee is a grid client that can do End-to-End application testing. 

Requirements
--------------------

 * Java 6 
 * git & maven -- if you want to build it yourself
 
Building
------------

Checkout from development branch:

    git clone git://github.com/grisu/gee.git
	
Building using maven:

    mvn clean install
	
if you also want to build deb and/or rpm packages:

    mvn clean install -Pdeb,rpm

Downloads
----------------

 * Windows (not recommended): [gee.msi](http://code.ceres.auckland.ac.nz/downloads/nesi/gee.msi)
 * Mac OS X: [gee.pgk](http://code.ceres.auckland.ac.nz/downloads/nesi/gee.pkg)
 * Linux: [deb](http://code.ceres.auckland.ac.nz/downloads/nesi/gee.deb), [rpm](http://code.ceres.auckland.ac.nz/downloads/nesi/gee.rpm) (also need: [nesi.deb](http://code.ceres.auckland.ac.nz/stable-downloads/nesi.deb) / [nesi.rpm](http://code.ceres.auckland.ac.nz/stable-downloads/nesi.rpm))
 * Cross-platform: [nesi-tools.jar](http://code.ceres.auckland.ac.nz/downloads/nesi/nesi-tools.jar)  (make sure to check 'gee' in the installer dialog)

Running tests
--------------------

## Folder structure

_Gee_ requires a folder hierarchy of applications/jobs/test. When running _Gee_, you need to specify the root of this hierarchy ("-f" flag). If you don't do that, _Gee_ assumes the current directory is that root folder. 

    Applications root folder
	 |
	 |------ scripts
	 |           |
	 |           | --- <check_script>
	 |           | --- <check_script>
	 |
	 | ------ < application name >
	 |                   |
	 |                   | ------ jobs
	 |                   |            | 
	 |                   |            |--- < job_name>
	 |                   |            |              | ----- job.config
 	 |                   |            |              |----- files	 
	 |                   |            |                         |---- <input_file>
	 |                   |            |                         |---- <input_file>
	 |                   |            |                         | ...
	 |                   |            |
	 |                   |            | 
	 |                   |            |--- < job_name>
	 |                   |                           | ----- job.config
 	 |                   |                           |----- files	 ( can also be text file with one path/url per line )
	 |                   |
	 |                   | ------   tests
	 |                                |
	 |                                | ---- < testname >
	 |                                |              | ----- submit.config
	 |                                |              | ----- checks.config
	 |                                |              |----- scripts	 
	 |                                |                         |---- <check_script>	 
	 |                                |                         |---- <check_script>	 	 
	 |                                |                         | ...
	 |                                |	 
 	 |                                | ---- < testname >
	 |                                |             | ----- submit.config
	 |                                |             | ----- checks.config
    ...
	...

### Creating test stub

_Gee_  provides a method to help creating this folder structure for each new test easily:

     gee --create-test-stub --application <application> --testname <name_of_the_test>

For example:

    think:~/apps $ gee --create-test-stub --application R --testname R_version_test
    Creating test stub for application R: R_version_test
    Stub created.
    think:~/apps $ find .
    .
    ./logs
    ./R
    ./R/jobs
    ./R/jobs/R_version_test
    ./R/jobs/R_version_test/job.config
    ./R/jobs/R_version_test/readme.txt
    ./R/jobs/R_version_test/files
    ./R/jobs/R_version_test/files/example_input_file.txt
    ./R/tests
    ./R/tests/R_version_test
    ./R/tests/R_version_test/checks.config
    ./R/tests/R_version_test/submit.config
    ./R/tests/R_version_test/scripts
    ./R/tests/R_version_test/scripts/check_for_some_string.py
    ./R/tests/R_version_test/scripts/readme.txt
    ./scripts
    ./scripts/is_not_true.py
    ./scripts/is_true.py

### List of files and their significance

#### _job.config_

The _job.config_ file sets up the job that will be submitted. Only job properties are specified, no submission-related ones. 

Example:

     // required parameters
     // -------------------
    
     commandline = cat example_input_file.txt
     application = generic
     walltime = 10h
    
     // optional parameters
     // --------------------
    
     // applicationVersion = 1.6.0
     // cpus = 1
     // email_address = m.binsteiner@auckland.ac.nz
     // email_on_job_finish = false
     // email_on_job_start = false
     // env = [LL_VAR=requirements=(Feature=="sandybridge")][other_env=othervalue][....]
     // mpi = false
     // single = false
     // hostCount = 0
     // memory = 2g
     // stderr = stderr.txt
     // stdout = stdout.txt
     // stdin = input.txt
     // virtualMemory = 2g

#### _files_ folder or file

A folder containing all input files that are required for the job. Can also be a text-file called 'files' with one url/path per line. All the files specified here will be uploaded to the working directory of the job.

#### _submit.config_

A file containing submission related properties (group, queue, ...). Example:

    job = R/jobs/R_version_test   // relative from application root folder, or absolute
    group = /nz/nesi
    queue = pan:pan.nesi.org.nz
	jobname = r_version_test   // optional, if not specified the name of the test folder is used

#### _checks.config_

A file describing all the checks that should be run against the result(s) of the test job. Example:

    # there is a global 'checks' directory in the root of your tests
    is_true.py exists(stdout.txt)

    # we can either use an existing application directly
    !grep world file(stdout.txt)

    # or, in some cases we might have to write a script
    check_hello_string=check_for_some_string.py hello content(stdout.txt)

Rules:
 * checks are looked for in the 'scripts'  subfolder of the test, in the global 'scripts' folder or in the PATH (in that order)
 * exit code needs to be 0 (if you expect non-zero exit code of the script, prepend a '!' before the check executable
 * you can use exists(<file>), file(<file), content(<file>) and filesize(<file>) in your check description, gee will replace the result of the method specified with this. <file> is always relative to the working directory of the job
 * you can name your check, for clearer log output. Do that by prepending <check_name>= before the check itself
 * if a check fails, possible stdout & stderr of the check itself are logged

#### _scripts_ (global & test specific)

A folder containing executables or scripts that are used to determine whether a test has failed or not. Make sure to set the executable flag!

## Running tests

A simple

    gee
	
runs all tests _Gee_ can find under the current directory. If the applications root folder is in a different location, use:

    gee -f <path_to_applications_root>
	
In order to only run tests for one application, you can filter like so:

    gee --application <application_name>
	
If you only want to run test(s) with a certain name, do:

    gee --testname <name_of_test>
	
You can use the _--application_ and _--testname_ flags in combination to filter even more finegrained.



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
	
Running tests
--------------------

### Folder structure

You have to create a folder hierarchy of applications/tests for _gee_:

    Tests root folder
	 |
	 | ------ < application name >
	 |                   |
	 |                   | ------   tests
	 |                                |
	 |                                | ---- < testname >
	 |                                |              | ----- job.config
	 |                                |              | ----- checks.config
 	 |                                |              |----- files	 
	 |                                |                         |---- <input_file>
	 |                                |                         |---- <input_file>
	 |                                |                         | ...
	 |                                |              |----- checks	 
	 |                                |                         |---- <check_script>	 
	 |                                |                         |---- <check_script>	 	 
	 |                                |                         | ...
	 |                                |	 
 	 |                                | ---- < testname >
	 |                                |             | ----- job.config
	 |                                |             | ----- checks.config
    ...
	...
	
#### _job.config_

The _job.config_ file sets up the job that will be submitted. 

#### _checks.config_

#### _checks_

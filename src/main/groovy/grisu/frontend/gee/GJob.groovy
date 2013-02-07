package grisu.frontend.gee

import grisu.control.ServiceInterface
import grisu.frontend.control.login.ServiceInterfaceFactory;
import grisu.frontend.model.job.JobObject
import grisu.model.job.JobDescription

class GJob {

	private static getJobName(def folder) {

		File configFile = Gee.getConfigFile(folder, Gee.JOB_PROPERTIES_FILE_NAME)

		return configFile.getParentFile().getName()
	}



	private static getJobFolder(def folder) {

		File configFile = Gee.getConfigFile(folder, Gee.JOB_PROPERTIES_FILE_NAME)
		return configFile.getParentFile()
	}


	final String job_name
	final File job_folder

	final File job_properties_file
	final JobDescription job_description
	final File files_folder

	File submit_properties_file

	/**
	 * @param file either the job.config file, it's parent directory as a file or as a path string
	 */
	public GJob(def folder) {

		// if the folder parameter is a submit config file, we parse this and take the job config from the job property there
		if ( folder instanceof String ) {
			folder = new File(folder)
		}
		if ( Gee.SUBMIT_PROPERTIES_FILE_NAME.equals(folder.getName()) ) {
			submit_properties_file = folder
			Map<String, String> temp = Gee.parsePropertiesFile(folder)
			def tempString = temp.get(Gee.JOB_KEY)
			if ( ! tempString ) {
				throw new RuntimeException("Submit config file does not have 'job' property but is used to create GJob object")
			}

			File tempFile = new File(tempString)
			if ( ! tempFile.exists() ) {
				tempFile = new File(submit_properties_file.getParentFile().getParentFile().getParentFile().getParentFile(), tempString)
				if ( !tempFile.exists() ) {
					throw new RuntimeException("Can't find job config: "+tempString)
				}
			}

			folder = tempFile
		}

		this.job_name = getJobName(folder)
		this.job_folder = getJobFolder(folder)
		this.job_properties_file = new File(this.job_folder, Gee.JOB_PROPERTIES_FILE_NAME)
		this.files_folder = new File(this.job_folder, Gee.FILES_DIR_NAME)

		//		def config = new ConfigSlurper().parse(job_properties_file.toURI().toURL()).flatten()
		Properties props = new Properties();
		FileInputStream fis = new FileInputStream(job_properties_file);

		//loading properites from properties file
		props.load(fis);

		this.job_description = new JobDescription(props.asImmutable())

		if ( files_folder.exists() ) {
			if ( files_folder instanceof File ) {

				if ( files_folder.isDirectory() ) {
					files_folder.eachFile { it ->
						this.job_description.addInputFile(it)
					}
				} else if ( files_folder.isFile() ) {
					files_folder.eachLine { it ->
						this.job_description.addInputFile(it)
					}
				
				}
			}

		}

	}

	/**
	 * Create job from an alternative submit_config file
	 * 
	 * @param si the serviceInterface
	 * @param submit_config the submit config file
	 * @return the JobObject
	 */
	public JobObject createJob(ServiceInterface si, def submit_config) {

		def submit_props = Gee.parsePropertiesFile(submit_config, Gee.SUBMIT_PROPERTIES_FILE_NAME)

		JobObject job = JobObject.createJobObject(si, this.job_description)

		def group = submit_props.get(Gee.GROUP_KEY)
		def queue = submit_props.get(Gee.QUEUE_KEY)
		def jobname = submit_props.get(Gee.JOBNAME_KEY)

		if ( ! jobname ) {
			jobname = Gee.getConfigFile(submit_config, Gee.SUBMIT_PROPERTIES_FILE_NAME).getParentFile().getName()
		}

		job.setTimestampJobname(jobname)

		job.setSubmissionLocation(queue)

		job.createJob(group)

		return job

	}

	public JobObject createJob(ServiceInterface si) {
		return createJob(si, submit_properties_file)
	}

}

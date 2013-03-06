package grisu.frontend.gee

import grisu.control.ServiceInterface
import grisu.frontend.control.login.LoginManager
import grisu.frontend.control.login.ServiceInterfaceFactory
import grisu.frontend.model.job.GrisuJob
import grisu.jcommons.constants.Constants
import grisu.jcommons.utils.PackageFileHelper
import grisu.jcommons.view.html.VelocityUtils
import grisu.model.job.JobDescription

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.google.common.collect.Maps

class GJob {
	
	static main(args) {
		
				LoginManager.initGrisuClient("gjob");
		
				ServiceInterface si = LoginManager.login('bestgrid', false)
				
				GJob job = new GJob(args[0])
				
				GrisuJob gj = job.createJob(si, args[0])
				
				gj.submitJob(true)
				
				println 'submitted'
		
			}
	
	public final static Logger myLogger = LoggerFactory.getLogger(GJob.class)

	public final static String JOB_KEY = "job"
	public final static String JOB_PROPERTIES_FILE_NAME = "job.config"
	public final static String SUBMIT_PROPERTIES_FILE_NAME = "submit.config"

	public final static String FILES_DIR_NAME = "files"



	private static getJobName(def folder) {

		File configFile = Gee.getConfigFile(folder, JOB_PROPERTIES_FILE_NAME)

		return configFile.getParentFile().getName()
	}



	private static getJobFolder(def folder) {

		File configFile = Gee.getConfigFile(folder, JOB_PROPERTIES_FILE_NAME)
		return configFile.getParentFile()
	}

	public static void createJobStub(File job_folder, String jobname) {

		if (job_folder.exists()) {
			println("Jobs folder already exists, not creating new one.")
			throw new RuntimeException("Job folder already exists: "+job_folder.getAbsolutePath())
		} else {
			job_folder.mkdirs()
			if (! job_folder.exists()) {
				throw new RuntimeException("Can't create directory "+job_folder.getAbsolutePath()+".")
			}
		}

		File temp = null
		temp = PackageFileHelper.getFile('job.config')
		new File(job_folder, JOB_PROPERTIES_FILE_NAME) << temp.text
		temp = new File(job_folder, FILES_DIR_NAME)
		temp.mkdirs()
		if ( ! temp.exists() ) {
			println ("Can't create folder: "+temp.getAbsolutePath())
			System.exit(1)
		}

		temp = PackageFileHelper.getFile('readme_files.txt')
		new File(job_folder, 'readme.txt') << temp.text

		File input_files = new File(job_folder, FILES_DIR_NAME)
		input_files.mkdirs()
		if ( ! input_files.exists() ) {
			println ("Can't create folder: "+input_files)
			System.exit(1)
		}
		temp = PackageFileHelper.getFile('example_input_file.txt')
		new File(input_files, 'example_input_file.txt') << temp.text

		Map properties = Maps.newHashMap()
		properties.put('job_dir', ".")
		String configContent = VelocityUtils.render('submit.config', properties)
		new File(job_folder, SUBMIT_PROPERTIES_FILE_NAME) << configContent
	}
	
	private static File parseJobFromSubmitPropertiesFile(File spf) {
		Map<String, String> temp = Gee.parsePropertiesFile(spf)
		def tempString = temp.get(JOB_KEY)
		if ( ! tempString ) {
			throw new RuntimeException("Submit config file does not have 'job' property but is used to create GJob object")
		}

		tempString = tempString.replace("/", File.separator)
		if (! tempString.endsWith(JOB_PROPERTIES_FILE_NAME) ) {
			tempString = tempString+File.separator+JOB_PROPERTIES_FILE_NAME
		}

		File tempFile = new File(tempString)

		if ( ! tempFile.exists() ) {
			tempFile = new File(spf.getParentFile().getPath()+File.separator+tempString)
			if ( !tempFile.exists() ) {
				throw new RuntimeException("Can't find job config: "+tempString)
			}
		}

		return tempFile
	}


	final String job_name
	final File job_folder

	final File job_properties_file
	JobDescription job_description
	final File files_folder
	
	final File submit_properties_file
	
	final Map<String, String> properties

	/**
	 * @param file either the submit.config, the job.config file, it's parent directory as a file or as a path string
	 */
	public GJob(def folder) {

		// if the folder parameter is a submit config file, we parse this and take the job config from the job property there
		if ( folder instanceof String ) {
			folder = new File(folder)
		}

		if ( folder.isFile() && ! JOB_PROPERTIES_FILE_NAME.equals(folder.getName()) ) {
			this.submit_properties_file = folder
			myLogger.debug("Parsing submit properties file for job...")
			
			folder = parseJobFromSubmitPropertiesFile(folder)
			
			myLogger.debug("Job config in: "+folder.getAbsolutePath())

		} else if ( folder.isDirectory() && new File(folder, SUBMIT_PROPERTIES_FILE_NAME).exists() ){
			this.submit_properties_file = new File(folder, SUBMIT_PROPERTIES_FILE_NAME)
			folder = parseJobFromSubmitPropertiesFile(this.submit_properties_file)
			
		} else {
			this.submit_properties_file = null
		}

		if ( JOB_PROPERTIES_FILE_NAME.equals(folder.getName()) ) {
			this.job_properties_file = folder
			
		} else {
			job_properties_file = new File(folder, JOB_PROPERTIES_FILE_NAME)
		}

		if ( ! job_properties_file.exists() || ! job_properties_file.isFile() || !job_properties_file.canRead() ) {

			throw new RuntimeException("job properties file does not exist or can't be read: "+job_properties_file)
		}



		this.job_name = getJobName(job_properties_file)
		this.job_folder = getJobFolder(job_properties_file)
		this.files_folder = new File(this.job_folder, FILES_DIR_NAME)
		
		this.properties = Gee.parsePropertiesFile(job_properties_file)
		

	}


	/**
	 * Create job from an alternative submit_config file
	 * 
	 * @param si the serviceInterface
	 * @param submit_config the submit config file
	 * @return the GrisuJob
	 */
	public GrisuJob createJob(ServiceInterface si, def submit_config=this.submit_properties_file, boolean createJobOnBackend=true) {

		if (!submit_config) {
			throw new RuntimeException("No submit config specified.")
		}

		def submit_props = submit_config
		if ( ( submit_config instanceof File ) || ( submit_config instanceof String ) ) {
			if (submit_config instanceof String) {
				submit_config = new File(submit_config)
			}
			if ( submit_config.isDirectory() ) {
				submit_props = Gee.parsePropertiesFile(submit_config, SUBMIT_PROPERTIES_FILE_NAME)
			} else {
				submit_props = Gee.parsePropertiesFile(submit_config)
			}
		} else if ( ! submit_config instanceof Map ) {
			throw new RuntimeException("Can't figure out class of submit_config: "+submit_config.toString())
		}

		def group = submit_props.get(Gee.GROUP_KEY)
		submit_props.remove(Gee.GROUP_KEY)
		if ( ! group ) {
			group = submit_props.get(Constants.FQAN_KEY)
			submit_props.remove(Constants.FQAN_KEY)
		}
		def queue = submit_props.get(Gee.QUEUE_KEY)
		submit_props.remove(Gee.QUEUE_KEY)
		if (! queue ) {
			queue = submit_props.get(Constants.SUBMISSIONLOCATION_KEY)
			submit_props.remove(Constants.SUBMISSIONLOCATION_KEY)
		}
		def jobname = submit_props.get(Gee.JOBNAME_KEY)
		submit_props.remove(Gee.JOBNAME_KEY)


		//overwrite generic properties with submission specific ones
		def props = this.properties.clone()
		props << submit_props

		this.job_description = new JobDescription(props)

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

		GrisuJob job = GrisuJob.createJobObject(si, this.job_description)

		if ( ! jobname ) {
			if ( submit_config.isDirectory() ) {
				jobname = submit_config.getName()
			} else {
				jobname = submit_config.getParentFile().getName()
			}
			
		}

		if ( ! job.getApplication() ) {
			job.setApplication(Constants.GENERIC_APPLICATION_NAME)
		}

		job.setTimestampJobname(jobname)

		job.setSubmissionLocation(queue)

		if ( createJobOnBackend ) {
			job.createJob(group)
		}

		return job

	}


}

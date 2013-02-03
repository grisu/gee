package grisu.frontend;

import grisu.frontend.view.cli.GrisuCliParameters;

import com.beust.jcommander.Parameter;

public class GeeCliParameters extends GrisuCliParameters {

	@Parameter(names = { "-f", "--folder" }, description = "folder containing the test")
	private String file;

	public String getFile() {
		return file;
	}

}

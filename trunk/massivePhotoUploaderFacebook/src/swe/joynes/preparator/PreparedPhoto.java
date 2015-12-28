package swe.joynes.preparator;

public class PreparedPhoto {

	public String description;
	public String path;
	
	public PreparedPhoto(String replaceFirst, String fileName) {
		description = replaceFirst;
		path = fileName; 
	}

}

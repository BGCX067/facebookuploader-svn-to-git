package swe.joynes.preparator;
import java.util.List;

import org.apache.log4j.Logger;


public class PreparedAlbum {

	public String albumName;
	public String description;
	public List<PreparedPhoto> photos;
	
	private Logger _log = Logger.getLogger(getClass());
	
	public PreparedAlbum(String replace, String description2, List<PreparedPhoto> files) {
		albumName = replace;
		photos = files;
		description = description2;
	}

	/**
	 * Print info about this album
	 */
	public String print()
	{
		StringBuffer str = new StringBuffer();
		str.append("\n------------------------------------");
		for (PreparedPhoto photo : photos)
		{
			str.append(photo.description + ": " + photo.path + "\n");
		}
		str.append("------------------------------------\n");
		return str.toString();
		
	}
	

}

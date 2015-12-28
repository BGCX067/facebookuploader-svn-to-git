package swe.joynes.uploader;

import com.facebook.api.FacebookException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

import java.util.Map;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import org.apache.log4j.Logger;

import swe.joynes.preparator.Prepare;
import swe.joynes.preparator.PreparedAlbum;
import swe.joynes.preparator.PreparedPhoto;
import uk.me.phillsacre.WorkingFacebookRestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import uk.me.phillsacre.Constants;
import uk.me.phillsacre.ImageUtils;
import uk.me.phillsacre.Photo;
import uk.me.phillsacre.PhotoImpl;
import uk.me.phillsacre.PropertyUtils;
import uk.me.phillsacre.XMLUtils;

/**
 * Scans a directory for albums and uploads them on facebook
 * @author joynes
 *
 */
public class BatchUploader {

    WorkingFacebookRestClient _facebookClient;
    Prepare _albums;
    private int PHOTO_MAX_HEIGHT;
    private int PHOTO_MAX_WIDTH;
    final private int SLEEP_TIME = 2000;
    private Logger _log = Logger.getLogger(getClass());
    JTextArea jTextArea1;
    JFrame _mainFrame;
    JProgressBar _jProgressBar;
    int picturesUploaded = 0;
    PropertyUtils _props;
    private int photoSize;

    /**
     * Inits the batch uploader 
     * @param facebookClient client initated and loggedin elsewhere
     * @param albums prepared structure with all album and photo information
     * @param jTextArea a GUI area to put information of what is happening
     * @param mainFrame a place to create POPUPS to display important messages
     * @throws java.lang.Exception
     */
    public BatchUploader(WorkingFacebookRestClient facebookClient, Prepare albums, JTextArea jTextArea, JFrame mainFrame, JProgressBar jProgressBar) throws Exception {
        _albums = albums;
        _facebookClient = facebookClient;
        jTextArea1 = jTextArea;
        _mainFrame = mainFrame;
        _jProgressBar = jProgressBar;

        _props = new PropertyUtils();
        PHOTO_MAX_HEIGHT = Integer.parseInt(_props.getProperty(Constants.Properties.MAX_DIMENSION));
        PHOTO_MAX_WIDTH = PHOTO_MAX_HEIGHT;

        photoSize = _albums.photoSize;
        try {
            printInfo("Uploading");
            AlbumsHandler();

            printInfo("Uploading finnished successfully!");
            alert("Uploading finnished successfully!");
        } catch (Exception ex) {
            printInfo("Error: " + ex.getMessage());
            _log.error("Here: " + ex, ex);

            String errorText = "";
            if (ex.getMessage().contains("Session key invalid")) {
                errorText = "Resetting sessionkey, Please click upload again! - ";
                _props.setProperty(Constants.Properties.SESSION_PERSISTENT_KEY, "");
                _props.setProperty(Constants.Properties.SESSION_SECRET_KEY, "");
            }
            alert("The albums was not successfully uploaded. \nTo recover from this login to facebook and remove the last created album because all photos might not be included. \nWhen done rerun this program to continue the synchronization!");
            alert("Error: " + errorText + ex.getMessage());
        }
    }

    /**
     * Gets all the albums lying in a directory. If an album with that name does
     * not exist on facebook then it uploads the album. If the pictures are more
     * then 604x604 then they will resized to fit in that range 
     * (recommended by facebook)
     * 
     * If something goes wrong during any picture for an album then further 
     * processeing will be discarded.
     *
     */
    public void AlbumsHandler() throws Exception {
        _log.info("BatchUploading");

        // get all existing albums to check if the album already exists
        List<String> existingAlbumNames = getUserAlbums();

        List<PreparedAlbum> nonExistingAlbums = new LinkedList<PreparedAlbum>();
        // iterate all albums and find out which to process
        for (PreparedAlbum preparedAlbum : _albums.albums) {

            String albumName = preparedAlbum.albumName;

            boolean isExisting = false;
            // check if the album already exists
            for (String existingAlbumName : existingAlbumNames) {
                if (existingAlbumName.equals((albumName))) {
                    isExisting = true;
                    _log.info("Album " + albumName + " already exists so skipping it");
                    printInfo("Synchronize: Album " + albumName + " already exists so skipping it");
                    photoSize -= preparedAlbum.photos.size();
                }
            }

            if (isExisting == false) {
                nonExistingAlbums.add(preparedAlbum);
                //albumHandler(preparedAlbum);

            }

        }
        // handle all albums that didnt exist
        for (PreparedAlbum preparedAlbum : nonExistingAlbums) {
            albumHandler(preparedAlbum);
        }

    }

    /**
     * Resize all the photos into a temp path and add them to the album. 
     * Then upload the album
     * @param preparedAlbum
     * @throws IOException 
     * @throws InterruptedException 
     */
    private void albumHandler(PreparedAlbum preparedAlbum) throws IOException, InterruptedException, FacebookException, Exception {

        List<Photo> photoList = new LinkedList<Photo>();
        String tmpPath = System.getProperty("java.io.tmpdir");

        // Copy all the pictures to a temp folder and resize them
        _log.info("Resizing pictures and put in " + tmpPath);
        printInfo("Resizing pictures and put in " + tmpPath);

        for (PreparedPhoto preparedPhoto : preparedAlbum.photos) {
            String tmpPhotoPath = tmpPath + "/" + preparedPhoto.description + ".jpg";
            String photoPath = preparedPhoto.path;
            copyfile(photoPath, tmpPhotoPath);

            Photo photo = new PhotoImpl(new File(tmpPhotoPath));
            printInfo("Resizing: " + preparedPhoto.description);

            ImageUtils.resizeImage(photo, PHOTO_MAX_HEIGHT, PHOTO_MAX_WIDTH);
            photo.setCaption(preparedPhoto.description);
            photoList.add(photo);
        }

        // sleep a little before and after uploading.. seems that it was doing things to fast..
        printInfo("Creating Facebook album: " + preparedAlbum.albumName);
        _facebookClient.photos_createAlbum(preparedAlbum.albumName, preparedAlbum.description, null);

        printInfo("Waiting..." + SLEEP_TIME / 1000 + " seconds");
        Thread.sleep(SLEEP_TIME);

        printInfo("Starting uploads!");

        // get albumId
        long albumId = getUserAlbumId(preparedAlbum.albumName);
        if (albumId == -1) {
            throw new Exception("Could not find album " + preparedAlbum.albumName +
                    "though it should been created");
        }

        // go through all photos and upload them
        for (PreparedPhoto photo : preparedAlbum.photos) {
            printInfo("Upload: " + photo.description);
            _jProgressBar.setValue((int) (((float) picturesUploaded++ / (float) photoSize) * 100F));
            _facebookClient.photos_upload(new File(photo.path), photo.description, new Long(albumId));
        }
    }

    /**
     * Copy one file to another location
     * @param srFile
     * @param dtFile
     * @throws IOException
     */
    private void copyfile(String srFile, String dtFile) throws IOException {
        File f1 = new File(srFile);
        File f2 = new File(dtFile);
        InputStream in = new FileInputStream(f1);

        //For Overwrite the file.
        OutputStream out = new FileOutputStream(f2);

        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    /**
     * Get all album names from current user in facebook. Pretty ugly!
     * @return
     */
    public List<String> getUserAlbums() throws FacebookException, IOException {
        List<String> albums = new LinkedList<String>();
        int userId;
        userId = _facebookClient.users_getLoggedInUser();
        Document doc = _facebookClient.photos_getAlbums(userId);

        NodeList nl = doc.getElementsByTagName("album");
        int length = nl.getLength();
        for (int i = 0; i < length; i++) {
            Node node = nl.item(i);
            Map<String, String> childData = XMLUtils.getChildValueMap(node);
            albums.add(childData.get("name"));
        }

        return albums;
    }

    /**
     * Get Id of the album with that name. This is an ugly way to do it but Im
     * short of time
     * @return
     */
    public long getUserAlbumId(String albumname) throws FacebookException, IOException {
        List<String> albums = new LinkedList<String>();
        int userId;
        userId = _facebookClient.users_getLoggedInUser();
        Document doc = _facebookClient.photos_getAlbums(userId);

        NodeList nl = doc.getElementsByTagName("album");
        int length = nl.getLength();
        for (int i = 0; i < length; i++) {
            Node node = nl.item(i);
            Map<String, String> childData = XMLUtils.getChildValueMap(node);

            if (childData.get("name").equals(albumname)) {
                return Long.parseLong(childData.get("aid"));
            }
        }
        return -1;
    }

    /**
     * Starts a thread that uploads all albums and can at the same time write
     * information to the GUI. 
     */
    public void run() {
        try {
            printInfo("Uploading");
            AlbumsHandler();

            printInfo("Uploading finnished successfully!");
            alert("Uploading finnished successfully!");
        } catch (Exception ex) {
            printInfo("Error: " + ex.getMessage());
            _log.error("Here: " + ex);

            String errorText = "";
            if (ex.getMessage().contains("Session key invalid")) {
                errorText = "Resetting sessionkey, Please click upload again! - ";
                _props.setProperty(Constants.Properties.SESSION_PERSISTENT_KEY, "");
                _props.setProperty(Constants.Properties.SESSION_SECRET_KEY, "");
            }
            alert("Error: " + errorText + ex.getMessage());
        }
    }

    /**
     * POPUP a message alert
     * @param str
     */
    public void alert(String str) {
        JOptionPane.showMessageDialog(_mainFrame, str);
    }

    /**
     * Print info to the gui, scroll down the textarea and do normal log.
     * @param str
     */
    public void printInfo(String str) {
        jTextArea1.setText(jTextArea1.getText() + "\n" + str);
        jTextArea1.setCaretPosition(jTextArea1.getDocument().getLength());
        _log.info(str);
    }
}

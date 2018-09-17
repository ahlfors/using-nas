package rzhang.nas.demo;


import com.aliyun.fc.runtime.FunctionComputeLogger;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.UUID;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class ImageCrawler {

    private static final String IMAGE_FOLER = "/img/";
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/535.1 (KHTML, like Gecko) Chrome/13.0.782.112 Safari/535.1";
    private File storageFolder;
    private HashSet<String> links = new HashSet<String>();
    private FunctionComputeLogger logger;
    private int cutoffSize; //minimum size of the image to download
    private boolean debug; //log level


    public ImageCrawler(String rootDir, int cutoffSize, boolean debug,
        FunctionComputeLogger logger) {
        this.logger = logger;
        this.debug = debug;
        this.cutoffSize = cutoffSize;
        storageFolder = new File(rootDir + IMAGE_FOLER);
        if (!storageFolder.exists()) {
            storageFolder.mkdirs();
        }
    }

    private static String getMD5(byte[] content) {
        StringBuffer sb = new StringBuffer();
        java.security.MessageDigest md5 = null;
        try {
            md5 = java.security.MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            return UUID.nameUUIDFromBytes(content).toString();
        }
        byte[] array = md5.digest(content);
        for (int i = 0; i < array.length; ++i) {
            sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1, 3));
        }
        return sb.toString();
    }

    private void logDebug(String logLine) {
        if (debug) {
            logger.debug(logLine);
        }
    }

    /**
     * This performs all the work. It makes an HTTP request, checks the response, and then gathers
     * up all the links on the page. Perform a searchForWord after the successful crawl
     *
     * @param url - The URL to visit
     * @return whether or not the crawl was successful
     */
    public HashSet<String> crawl(String url) {
        links.clear();
        try {
            Connection connection = Jsoup.connect(url).userAgent(USER_AGENT);
            Document htmlDocument = connection.get();
            logger.info("Got `" + url + "` with title = " + htmlDocument.title());
            Elements media = htmlDocument.select("[src]");
            for (Element src : media) {
                if (src.tagName().equals("img")) {
                    downloadImage(src.attr("abs:src"));
                }
            }
            Elements linksOnPage = htmlDocument.select("a[href]");
            for (Element link : linksOnPage) {
                logDebug("Plan to crawl `" + link.absUrl("href") + "`");
                this.links.add(link.absUrl("href"));
            }

        } catch (IOException ioe) {
            // We were not successful in our HTTP request
            logger.error("Failed to crawl `" + url + "` due to " + ioe.getLocalizedMessage());
        }
        logDebug("Find " + links.size() + " more links.");
        return links;
    }

    private boolean downloadImage(String imageUrl) throws IOException {
        logDebug("Try to download `" + imageUrl + "`.");
        URL url = new URL(imageUrl);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] content;
        byte[] data = new byte[1024];
        int nRead;
        try (InputStream is = url.openStream()) {
            while (-1 != (nRead = is.read(data, 0, data.length))) {
                buffer.write(data, 0, nRead);
            }
        }
        content = buffer.toByteArray();
        if (content.length < 1024 * cutoffSize) {
            logDebug("Skip `" + imageUrl + "` as it's not big enough");
            return true;
        }
        logDebug("Downloaded `" + imageUrl + "`. The total size is " + content.length);
        String hashedName = storageFolder.getAbsolutePath() + "/" + getMD5(content);
        // store the image
        String ext = imageUrl.substring(imageUrl.lastIndexOf('.'));
        ext = ext.substring(0, (ext.chars().anyMatch(c -> (c == '?' || c == '$'))) ? Math
            .max(ext.indexOf('?'), ext.indexOf('$')) : ext.length());
        String filename = hashedName + ext;
        if (!new File(filename).exists()) {
            logger.info("Write `" + imageUrl + "` to " + filename);
            Files.write(new File(filename).toPath(), content);
        } else {
            logDebug(filename + " already exits");
        }
        return true;
    }

}

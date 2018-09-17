package rzhang.nas.demo;

import com.aliyun.fc.runtime.Context;
import com.aliyun.fc.runtime.FunctionComputeLogger;
import com.aliyun.fc.runtime.PojoRequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ImageCrawlerHandler implements PojoRequestHandler<TimedCrawlerConfig, CrawlingResult> {

    private static String CRAWL_HISTORY = "/.history";
    private static String CRAWL_WORKITEM = "/.workitem";
    private static int MAX_PAGE_CRAWAL = 100000;
    private static ObjectMapper JSON_MAPPER = new ObjectMapper();


    private Set<String> pagesVisited = new HashSet<>();
    private List<String> pagesToVisit = new LinkedList<>();
    private FunctionComputeLogger logger;
    private String rootDir = new String();
    private boolean maxWorkitemReached = false;

    /**
     * Returns the next URL to visit (in the order that they were found). We also do a check to make
     * sure this method doesn't return a URL that has already been visited.
     */
    private String nextUrl() {
        String nextUrl;
        boolean validURL;
        do {
            nextUrl = pagesToVisit.isEmpty() ? "" : pagesToVisit.remove(0);
            validURL = true;
            try {
                URL u = new URL(nextUrl);
                u.toURI();
            } catch (URISyntaxException | MalformedURLException e) {
                validURL = false;
            }
        } while (pagesVisited.contains(nextUrl) || !validURL);
        return nextUrl;
    }

    private void initializePages(String rootDir) throws IOException {
        if (this.rootDir.equalsIgnoreCase(rootDir)) {
            logger.info("Already initilized");
            return;
        }
        try {
            //read crawl history
            pagesVisited.clear();
            new BufferedReader(new FileReader(rootDir + CRAWL_HISTORY)).lines()
                .forEach(l -> pagesVisited.add(l));
            logger.info(
                "Grabbed the crawl history at " + rootDir + CRAWL_HISTORY + " with " + pagesVisited
                    .size() + " pages visited");
            //read crawl workitems
            pagesToVisit.clear();
            new BufferedReader(new FileReader(rootDir + CRAWL_WORKITEM)).lines()
                .forEach(l -> pagesToVisit.add(l));
            logger.info(
                "Grabbed the workitems at " + rootDir + CRAWL_WORKITEM + " with " + pagesToVisit
                    .size() + " pages to visit");
        } catch (FileNotFoundException e) {
            logger.info(e.toString());
        }
        if (pagesToVisit.size() > MAX_PAGE_CRAWAL) {
            logger.info("Reached max workitems");
            maxWorkitemReached = true;
        }
        this.rootDir = rootDir;
    }

    private void saveHistory(String rootDir, String justVistedPage, HashSet<String> newPages)
        throws IOException {
        //append crawl history to the end of the file
        try (PrintWriter pvfw = new PrintWriter(
            new BufferedWriter(new FileWriter(rootDir + CRAWL_HISTORY, true)));
        ) {
            pvfw.println(justVistedPage);
            pvfw.flush();
            logger.info("Saved the newly crawlled page to history at " + rootDir + CRAWL_HISTORY);
        }
        if (maxWorkitemReached) {
            return;
        }
        //append to be crawled workitems to the end of the file
        try (PrintWriter ptfw = new PrintWriter(
            new BufferedWriter(new FileWriter(rootDir + CRAWL_WORKITEM, true)));
        ) {
            newPages.stream().forEach(p -> ptfw.println(p));
            ptfw.flush();
        }
        logger.info("Saved " + newPages.size() + " workitems");
    }


    @Override
    public CrawlingResult handleRequest(TimedCrawlerConfig timedCrawlerConfig, Context context) {
        CrawlingResult crawlingResult = new CrawlingResult();
        this.logger = context.getLogger();
        CrawlerConfig crawlerConfig = null;
        try {
            crawlerConfig = JSON_MAPPER.readerFor(CrawlerConfig.class)
                .readValue(timedCrawlerConfig.payload);
        } catch (IOException e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            crawlingResult.errorStack = sw.toString();
            return crawlingResult;
        }
        logger.info("Start crawl job at " + timedCrawlerConfig.triggerTime);
        logger.info("We will search for " + crawlerConfig.numberOfPages + " pages");
        logger.info("And download images that is at least " + crawlerConfig.cutoffSize + "kB");
        logger.info("Debug =  " + crawlerConfig.debug);
        ImageCrawler crawler = new ImageCrawler(
            crawlerConfig.rootDir, crawlerConfig.cutoffSize, crawlerConfig.debug, logger);
        int pagesCrawled = 0;
        try {
            initializePages(crawlerConfig.rootDir);
            if (pagesToVisit.isEmpty()) {
                logger.info("Let the crawl begin at " + crawlerConfig.url);
                pagesToVisit.add(crawlerConfig.url);
            }
            while (pagesCrawled < crawlerConfig.numberOfPages) {
                String currentUrl = nextUrl();
                if (currentUrl.isEmpty()) {
                    logger.info("Finished crawling `" + crawlerConfig.url + "` after visiting "
                        + pagesVisited.size() + " pages");
                    break;
                }
                logger.info("Start to crawl " + currentUrl);
                HashSet<String> newPages = crawler.crawl(currentUrl);
                newPages.stream().forEach(p -> {
                    if (!pagesVisited.contains(p)) {
                        pagesToVisit.addAll(newPages);
                    }
                });
                pagesCrawled++;
                pagesVisited.add(currentUrl);
                saveHistory(crawlerConfig.rootDir, currentUrl, newPages);
            }
            // calculate the total size of the images
            crawlingResult.totalImageSize = Files.walk(new File(crawlerConfig.rootDir).toPath())
                .mapToLong(p -> {
                    if (!p.toFile().isDirectory()) {
                        crawlingResult.totalImageDownloaded++;
                        return p.toFile().length();
                    } else {
                        return 0l;
                    }
                }).sum();
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            crawlingResult.errorStack = sw.toString();
        }

        crawlingResult.totalCrawlCount = pagesVisited.size();
        return crawlingResult;
    }
}



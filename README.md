This article showcases how you can utilize multiple unique features of the Alibaba Cloud Function Compute to build an image processing web service. 

## Introducing Function Compute 
[Function Compute](https://www.alibabacloud.com/product/function-compute) is an Alibaba cloud serverless platform that allows engineers to develop an internet scale service with just a few lines of code.  It seamlessly handles resource management, auto scaling and load balancing so that developers can focus on their business logic without worrying about managing the underlying infrastructure making it easy to build applications that respond quickly to new information”. Internally, we utilize container technology and develop proprietary distributed algorithms to schedule our user's code on resources that are scaled elastically. Since it's inception a little over an year ago, we have developed many cutting-edge technologies internally aiming to provide our users with high scalability, reliability and performance.

In this guide, we will show you a step by step tutorial that showcases some of it's innovative features. You can read this [quick start guide](https://www.alibabacloud.com/help/doc-detail/73329.htm) to familiarize yourself with basic serverless concepts if this is your first time using Function Compute.

## Using Network File System
The first [feature](https://www.alibabacloud.com/help/doc-detail/87401.htm) that we introduce will allow developers to write functions that read and write from a network attached file system like Alibaba Cloud [NAS](https://www.alibabacloud.com/product/nas).

### Motivation
The serverless nature of the platform means that user code *can* run on different instances each time it is invoked. This further implies that the functions cannot rely on its local file system to store any intermediate results. The developers have to rely on another cloud service like [Object Storage Services](https://www.alibabacloud.com/product/oss) to share processed results between functions or invocations. This is not ideal as dealing with another distributed service adds extra development overhead and complexities in the code to handle various edge cases.

To solve this problem, we developed the access Network Attached Storage ([NAS](https://www.alibabacloud.com/product/nas)) feature. NAS is another Alibaba cloud service that offers a highly scalable, reliable and available distributed file system that supports <strong>standard file access protocols</strong>. We can mount the remote NAS file system to the resource on which the user code is running which effectively creates a "local" file system for the function code to use. 

### Image Crawling Example
This demo section shows you how to create a serverless web crawler that downloads all the images starting from a seed webpage. This is a quite a challenge problem to be run on a serverless platform as it is not possible to crawl all the websites in one function given the time constraints. However, with the access to NAS feature, it becomes straightforward as one can use the NAS file system to share data between function runs. Below we show a step by step tutorial.  We assume that you understand the concept of [VPC] (https://www.alibabacloud.com/product/vpc) and know how to create a NAS [mount point] (https://www.alibabacloud.com/help/doc-detail/60431.htm) in a VPC.  Otherwise, you can read the [basic NAS tutorial] (https://www.alibabacloud.com/help/doc-detail/90025.htm) before proceeding to the steps below.

#### Create a service with NAS configuration
1. Log on to the [Function Compute console](https://fc.console.aliyun.com/).
2. Select the target region in which your NAS is located.
3. Create a service that uses a pre-created NAS file system. In this demo:
![Screen Shot 2018-09-11 at 11.35.38 PM.png](http://ata2-img.cn-hangzhou.img-pub.aliyun-inc.com/e4334f6e08bc1ef01d13a2e858e2931e.png)
	1. Enter the **Service Name** and **Description**.
	2. Enable **Advanced Settings**.
	3. Finish the **VPC Configs** fields, make sure that you select the VPC in which the NAS mount point is located. 
![Screen Shot 2018-09-11 at 11.37.28 PM.png](http://ata2-img.cn-hangzhou.img-pub.aliyun-inc.com/4bee41dfea1b1debcee011e8cec688f7.png)
       After the **VPC Configs** are complete, the **NAS Config** fields will appear.
4.  Complete the **Nas Config** fields as described below.
![NAS Config.jpg](http://ata2-img.cn-hangzhou.img-pub.aliyun-inc.com/27fc1fde83b1783c1556ec1602b9ac6e.jpg)
  1. The **UserId** and **GroupId** fields are the `uid`/`gid` under which the function runs. They determine the owner of all the files created on the NAS file system. You can pick any user/group id for this demo as they are shared among all functions in this service.
  2. The **NAS Mount Point** drop down menu list all the valid NAS mount points that are accessible from the chosen VPC. 
  3. The **Remote Path** is a directory on the NAS file system, it does not need to be the root directory of the NAS file system. Please choose a directory that you want to store the images.
  4. The **Local Mount Path** is the local directory where the function can access the remote directory and please remember what you choose here.

5. Complete the [Log Service] (https://www.alibabacloud.com/product/log-service) configuration with your desired logstore destination.
6.  Make sure that you config your [role](https://www.alibabacloud.com/help/doc-detail/52885.htm) to grant Function compute access to your VPC and logstore.
7. Click **OK**.

#### Create a function that starts every five minutes
Now that we have a service with NAS access, it's time to write the crawler.  Since the crawler function has to run many times before it can finish, we will use a [time trigger] (https://www.alibabacloud.com/help/doc-detail/68172.htm) to invoke it every 5 minutes.
1. Log on to the Function Compute console and select the service you just created
2. Create a [function] (https://www.alibabacloud.com/help/doc-detail/52077.htm) for the service by clicking the plus sign.
![Create Function.jpg](http://ata2-img.cn-hangzhou.img-pub.aliyun-inc.com/27b9f26856edc6954fb94f1327bb9d1f.jpg)
3. Function Compute provides various function templates to help you quickly build an application. Select to create an empty function for this demo and click next but you can play with other templates when you have time.
4. Select time trigger in the drop down menu in the next page. Fill out the trigger name and set the invoke interval to be 5 minutes and leave the events empty for now and click next.
![Time Trigger.jpg](http://ata2-img.cn-hangzhou.img-pub.aliyun-inc.com/3f1d86be7009aea17b8c6eab5e24216a.jpg)
5. Fill in the function name and make sure to select `java8` as the runtime. Also fill in the function handler and set the memory to be `2048MB` and Time out as `300` seconds and click next
![java function.jpg](http://ata2-img.cn-hangzhou.img-pub.aliyun-inc.com/81b5fbaceb8e96d1b55fb64d5bcd70b0.jpg)
6. Click next and make sure the preview looks good before clicking create.

#### Write the crawler in Java
Now you should see the function code page and it's time to write the crawler. The handler logic is pretty straightforward as shown below.
- Parse the time trigger event to get the crawler config.
- Create the image crawler based on the config. The crawler uses a [JAVA HTML Parser] (https://jsoup.org/) to parse html pages to identify images and links.
- Read the already and not-yet visited web page lists from the NAS file system (only if the function is running in a new environment).
- Continue the depth-first traverse of the web pages and use the crawler to download any new pictures along the way.
- Save the newly found web pages to the NAS file system.
Here is an excerpt of the Java code and you can see that we read and write files to the NAS file system exactly the same way as to the local file system.
```java
public class ImageCrawlerHandler implements PojoRequestHandler<TimedCrawlerConfig, CrawlingResult> {
    private String nextUrl() {
        String nextUrl;
        do {
            nextUrl = pagesToVisit.isEmpty() ? "" : pagesToVisit.remove(0);
        } while (pagesVisited.contains(nextUrl) );
        return nextUrl;
    }

    private void initializePages(String rootDir) throws IOException {
        if (this.rootDir.equalsIgnoreCase(rootDir)) {
            return;
        }
        try {
            new BufferedReader(new FileReader(rootDir + CRAWL_HISTORY)).lines()
                .forEach(l -> pagesVisited.add(l));
            new BufferedReader(new FileReader(rootDir + CRAWL_WORKITEM)).lines()
                .forEach(l -> pagesToVisit.add(l));
        } catch (FileNotFoundException e) {
            logger.info(e.toString());
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
        }
        //append to be crawled workitems to the end of the file
        try (PrintWriter ptfw = new PrintWriter(
            new BufferedWriter(new FileWriter(rootDir + CRAWL_WORKITEM, true)));
        ) {
            newPages.stream().forEach(p -> ptfw.println(p));
        }
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
            ....
        }
        ImageCrawler crawler = new ImageCrawler(
            crawlerConfig.rootDir, crawlerConfig.cutoffSize, crawlerConfig.debug, logger);
        int pagesCrawled = 0;
        try {
            initializePages(crawlerConfig.rootDir);
            if (pagesToVisit.isEmpty()) {
                pagesToVisit.add(crawlerConfig.url);
            }
            while (pagesCrawled < crawlerConfig.numberOfPages) {
                String currentUrl = nextUrl();
                if (currentUrl.isEmpty()) {
                    break;
                }
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
           .....
        } catch (Exception e) {
            crawlingResult.errorStack = e.toString();
        }

        crawlingResult.totalCrawlCount = pagesVisited.size();
        return crawlingResult;
    }
}
```
```java
public class ImageCrawler {
 ...
 public HashSet<String> crawl(String url) {
        links.clear();
        try {
            Connection connection = Jsoup.connect(url).userAgent(USER_AGENT);
            Document htmlDocument = connection.get();
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
           ...
        }
        return links;
    }
}
```
For the sake of simplicity, we have omitted some details and other helper classes. You can get all the code from the [awesome-fc github project](https://github.com/awesome-fc/using-nas) repo if you would like to run the code and get images from your favorite websites.

#### Run the crawler
Now that we have written the code, we need run it. Here are the steps.
-  We use *maven* to do dependency and build management. Just type the following command after you sync with the repro (assuming you have maven installed already) to create the jar file ready to upload.

```shell
mvn clean package
```
- Select the `Code` tab in the function page. Upload the jar file (the one with name ends with dependencies) created in the previous step through the console.
![java function.jpg](http://ata2-img.cn-hangzhou.img-pub.aliyun-inc.com/1ec6399a5a0c5f7f095e6a178841c85f.jpg)
- Select the `Triggers` tab in the function page. Click the time trigger link to enter the event in Json format. The Json event which will be serialized to the crawler config and passed to the function. Click Ok.
![timer event.jpg](http://ata2-img.cn-hangzhou.img-pub.aliyun-inc.com/f6733031ff3d2f760ecb4e29f9e419c3.jpg)
- The time trigger invokes the crawler function every five minutes.  Each time, the handler picks up the list of URLs still need to be visited and start from the first one.
- You can select the `Log` tab to search for the crawler execution log.

## Create a Serverless Service
The second [feature](https://www.alibabacloud.com/help/doc-detail/71229.htm) that we introduce allows anyone to send an HTTP request to trigger a function execution directly.

### Motivation
Now that we have a file system filled with the images downloaded from the web, we want to find a way to serve those images through a web service. The traditional way is to mount the NAS to a VM and start a webserver on it. This is both a waste of resources if the service is lightly used and not scalable when the traffic is heavy. Instead, you can write a serverless function that reads the images stored on the NAS file system and serve it through a HTTP endpoint. In this way, you can enjoy the instant scalability that Function Compute provides while still only pay for the actual usage.

### Image Processing Service Example
This demo shows how to write an Image Processing Service. 

#### Create a Function with HTTP Trigger
1. Log on to the Function Compute console and select the same service as the crawler function.
2. Create a [function](https://www.alibabacloud.com/help/doc-detail/52077.htm) for the service by clicking the plus sign.
3. Select to create an empty python2.7 function and click next.
4. Select HTTP trigger in the drop down menu and make sure that it supports both `GET` and `POST` invoke method and click next.
![HTTP Trigger.jpg](http://ata2-img.cn-hangzhou.img-pub.aliyun-inc.com/54a70c5ab70f0be46be31a6f91129115.jpg)
5. Finish the rest of the step and click OK.
6. Get the [files] (https://github.com/awesome-fc/using-nas/blob/master/python) from the same [github] (https://github.com/awesome-fc/using-nas) repro and upload the directory to the function.
![Upload Dir.jpg](http://ata2-img.cn-hangzhou.img-pub.aliyun-inc.com/3f6338a051d23f970ea3e93f9efec379.jpg)

#### Image Processing Using Python
Function Compute's [python](https://www.alibabacloud.com/help/doc-detail/56316.htm) runtime comes with many built-in modules that one can use. In this example, we use both [opencv](https://opencv-python-tutroals.readthedocs.io/en/latest/py_tutorials/py_setup/py_intro/py_intro.html) and [wand](http://docs.wand-py.org/en/0.4.4/) to do image transformations. 

#### Use the HTTP trigger in Python
Even with an image processing function, we still need to setup a web site to serve the requests. Normally, one needs to use another service like [API gateway](https://www.alibabacloud.com/help/doc-detail/54788.htm) to handle HTTP requests. In this demo, we are going to use the Function Compute [HTTP Trigger](https://www.alibabacloud.com/help/doc-detail/71229.htm) feature to allow a HTTP request to trigger a function execution directly. With the HTTP trigger, the headers/paths/query in the HTTP requests are all passed to the function handler directly and the function can return the HTML content dynamically. 

With these two features, the handler code is surprisingly straightforward and here is a high-level breakdown.
- Get the HTTP path and query from the system `environ` variable.
- Use the HTTP path to load the image on the NAS file system.
- Apply different image processing techniques based on the query `action`.
- Insert the transformed image onto the pre-build html file and return it.

Here is an excerpt of the handler logic and we can see that `wang` loads the image stored on NAS just like a normal file on the local system. 

```python
import cv2
from wand.image import Image

TEMPLATE = open('/code/index.html').read()
NASROOT = '/mnt/crawler'
face_cascade = cv2.CascadeClassifier('/usr/share/opencv/lbpcascades/lbpcascade_frontalface.xml')

def handler(environ, start_response):
    logger = logging.getLogger()
    context = environ['fc.context']
    path = environ.get('PATH_INFO', "/")
    fileName = NASROOT + path

    try:
        query_string = environ['QUERY_STRING']
        logger.info(query_string)
    except (KeyError):
        query_string = " "

    action = query_dist['action']
    
    if (action == "show"):
        with Image(filename=fileName) as fc_img:
            img_enc = base64.b64encode(fc_img.make_blob(format='png'))

    elif (action == "facedetect"):
        img = cv2.imread(fileName)
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        faces = face_cascade.detectMultiScale(gray, 1.03, 5)
        for (x, y, w, h) in faces:
            cv2.rectangle(img, (x, y), (x + w, y + h), (0, 0, 255), 1)
        cv2.imwrite("/tmp/dst.png", img)
        with open("/tmp/dst.png") as img_obj:
            with Image(file=img_obj) as fc_img:
                img_enc = base64.b64encode(fc_img.make_blob(format='png'))
    elif (action == "rotate"):
        assert len(queries) >= 2
        angle = query_dist['angle']
        logger.info("Rotate " + angle)
        with Image(filename=fileName) as fc_img:
            fc_img.rotate(float(angle))
            img_enc = base64.b64encode(fc_img.make_blob(format='png'))
    else:
        # demo, mixed operation
        
    status = '200 OK'
    response_headers = [('Content-type', 'text/html')]
    start_response(status, response_headers)
    return [TEMPLATE.replace('{fc-py}', img_enc)]

```

#### What Next ?
Now we have the function and the HTTP trigger ready,  we can try an advanced feature like [face detection](https://1986114430573743.cn-hongkong.fc.aliyuncs.com/2016-08-15/proxy/NASDemo/NASImageConverter/tmz/img/01b6a87e57b6da3b072fc10b1e45f37a.jpg?action=facedetect) or [image rotation](https://1986114430573743.cn-hongkong.fc.aliyuncs.com/2016-08-15/proxy/NASDemo/NASImageConverter/tmz/img/01b6a87e57b6da3b072fc10b1e45f37a.jpg?action=rotate&angle=90).
- The URL is constrained based on your user/region/service/function [name](https://www.alibabacloud.com/help/doc-detail/74771.htm).
- Use the relative path to the local NAS mount dir of any image. You can find out all the files on your NAS system through your crawler log.
- You can edit the python code on the Function Compute console directly and add many more different image transformations and play with it.

## Conclusions
- You can read the [blog](https://www.alibabacloud.com/blog/serverless-computing-with-alibaba-cloud-function-compute_593960) to get a more general idea what [Function Compute](https://www.alibabacloud.com/product/function-compute) can do.
- You can also read the official NAS [tutorial](https://www.alibabacloud.com/help/doc-detail/90025.htm) and other Function Compute [documentations](https://www.alibabacloud.com/help/doc-detail/52895.htm) to learn more exciting new features.
- Please give us feedbacks or suggestions in our official Function Compute [forum](https://www.alibabacloud.com/forum/thread-47) or the official Alibaba Cloud [Slack] (https://alibabaclouddeveloper.slack.com/join/shared_invite/enQtMzg1MTY5MjM3OTg4LTEyYzY5NDRjNmMzYWMyNWI0OTNmNTRiZDRjNDdhY2ZmNDJlNWViNmFlODBjYjAzNzJkZDg0NWI5ZmJmODcyMDI) Channel.

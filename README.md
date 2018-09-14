# NAS Demo
This is a demo that shows how to use the NAS feature in the Alibaba Cloud Function Compute to crawl a website. Since a crawler follows all links, it can run forever. Thus, we will use a time trigger to run the crawler every minute and use the NAS file system to store the images and the list of pages to crawl. 

##Introduce Function Compute
[Function Compute](https://www.alibabacloud.com/product/function-compute) is an Alibaba cloud serverless platform that allows engineers to develop an internet scale service with just a few lines of code.  It seamlessly handles the resource management, auto scaling, and load balancing so that developers can focus on their business logic and don't worry about managing cloud infrastructures. Internally, we deploy containers and use preprioritory distributed algorithms to schedule our users’ code on resources that are scaled up and down automatically. Since its inception a little over a year ago, we have developed many cutting-edge technologies internally aiming to provide our users with high scalability, reliability and performance.

In this guide, we show you a step by step tutorial that showcases some of its innovative features. You can read this [quick start guide](https://www.alibabacloud.com/help/doc-detail/73329.htm) to familiar yourself with basic serverless concepts if this is your first time using Function Compute.

## Using Network File System
The first [feature](https://www.alibabacloud.com/help/doc-detail/87401.htm) that we introduce allows developers to write function code that read and write from a network attached file system.

###Motivation
The serverless nature of the platform means that user code *can* run on different places each time it is invoked. This further implies that the functions cannot rely on its local file system to store any intermediate results. The developers have to rely on another cloud service like [Object Storage Services](https://www.alibabacloud.com/product/oss) to share running results between functions or invocations. This is not ideal as dealing with another distributed service adds extra developing overhead and complexities in the code in order to handle various edge cases.

To solve this problem, we developed the access Network Attached Storage ([NAS](https://www.alibabacloud.com/product/nas)) feature. NAS is another Alibaba cloud service that offers a highly scalable, reliable and available distributed file system that supports <strong>standard file access protocols</strong>. We mount the remote NAS file system to the resource that the user code is running which effectively creates a "local" file system for the function code to use. 

###Image Crawling Example
The demo shows how to create a serverless web crawler that downloads all the images from a seed webpage. This is a quite challenge problem as it is not possible to crawl all the websites in one function given the time constraints. However, with the access to NAS feature, it becomes straightforward as one can use the NAS file system to share data between function runs. Below we show a step by step tutorial from scratch.  We assume that you understand the concept of [VPC] (https://www.alibabacloud.com/product/vpc) and know how to create a NAS [mount point] (https://www.alibabacloud.com/help/doc-detail/60431.htm) in a VPC.

#### Create a service with NAS configuration
1. Log on to the [Function Compute console](https://fc.console.aliyun.com/).
2. Select the target region in which your NAS is located.
3. Create a service that uses a pre-created NAS file system. In this demo:
![Screen Shot 2018-09-11 at 11.35.38 PM.png](http://ata2-img.cn-hangzhou.img-pub.aliyun-inc.com/e4334f6e08bc1ef01d13a2e858e2931e.png)
	1. Enter the **Service Name** and **Description**.
	2. Enable **Advanced Settings**.
	3. Finish the **VPC Configs** field, make sure that you select the VPC in which the NAS mount point is located. After the **VPC Configs** are complete, the **NAS Config** fields appear.
![Screen Shot 2018-09-11 at 11.37.28 PM.png](http://ata2-img.cn-hangzhou.img-pub.aliyun-inc.com/4bee41dfea1b1debcee011e8cec688f7.png)

4.  Complete the **Nas Config** fields as described below.
![NAS Config.jpg](http://ata2-img.cn-hangzhou.img-pub.aliyun-inc.com/27fc1fde83b1783c1556ec1602b9ac6e.jpg)
  1. The **UserId** and **GroupId** field is the `uid`/`gid` under which the function runs. It determines the owner of all the files created on the NAS file system. You can pick any user/group id for this demo.
  2. The **NAS Mount Point** drop down menu has all the valid NAS mount points that are accessible from the chosen VPC. 
  3. The **Remote Path** is a directory on the NAS file system, it does not need to be the root directory of the NAS file system. Please choose a directory that you want to store crawling result.
  4. The **Local Mount Path** is the local directory where the function can access the remote directory and please remember what you choose here.

5. Complete the [log configuration] (https://www.alibabacloud.com/product/log-service) with your desired logstore destination.
6.  Make sure that you config your [role](https://www.alibabacloud.com/help/doc-detail/52885.htm) to grant Function compute access to your VPC and logstore.
7. Click **OK**.

#### Create a function that starts every five minutes
Now that we have a service that has NAS access, time to write the crawler.  Since the crawler function has to run many times before it can finish, we will use a [time trigger] (https://www.alibabacloud.com/help/doc-detail/68172.htm) to invoke it every 5 minutes.
1. Log on to the Function Compute console and select the service you just created
2. Create a [function] (https://www.alibabacloud.com/help/doc-detail/52077.htm) for the service by clicking the plus sign.
![Create Function.jpg](http://ata2-img.cn-hangzhou.img-pub.aliyun-inc.com/27b9f26856edc6954fb94f1327bb9d1f.jpg)
3. Function Compute provides various function templates to help you quickly build an application. Just select to create an empty function for this demo and click next.
4. Select time trigger in the drop down menu in the next page. Fill out the trigger name and set the invoke interval to be 5 minutes and leave the events empty for now and click next.
![Time Trigger.jpg](http://ata2-img.cn-hangzhou.img-pub.aliyun-inc.com/3f1d86be7009aea17b8c6eab5e24216a.jpg)
5. Fill in the function name and make sure to select `java8` as the runtime. Also fill in the function handler and set the memory to be `2048MB` and Time out as `300` seconds and click next
![java function.jpg](http://ata2-img.cn-hangzhou.img-pub.aliyun-inc.com/81b5fbaceb8e96d1b55fb64d5bcd70b0.jpg)
6. Click next and make sure the preview looks good before clicking create.

#### Write the crawler
Now you should see the function code page and it's time to write the crawler. The handler logic is pretty straightforward like below.
- Parse the time trigger event to get the crawler config
- Create the image crawler based on the config
- Read the already and not-yet visited web page lists from the NAS file system (only if the function is running in a new environment).
- Continue the depth-first traverse of the web pages and download any new pictures along the way.
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
We omitted some details and the other helper classes. You can get all the code from the [awesome-fc github project](https://github.com/awesome-fc/using-nas) if you would like to run the demo.  

#### Run the crawler
Now that we have written the code, we need run it. Here are the steps.
-  We use *maven* to do dependency and build management. Just type the following command after you sync with the repro (assuming you have maven installed already) to create the jar file ready to upload.

```shell
mvn clean package
```
- Select the `Code` tab in the function page. Upload the jar file (the one with name ends with dependencies) created in the previous step through the console.
![java function.jpg](http://ata2-img.cn-hangzhou.img-pub.aliyun-inc.com/1ec6399a5a0c5f7f095e6a178841c85f.jpg)
- Select the `Triggers` tab in the function page. Click the time trigger link to enter the event which will be serialized to the crawler config. Click Ok.
![timer event.jpg](http://ata2-img.cn-hangzhou.img-pub.aliyun-inc.com/f6733031ff3d2f760ecb4e29f9e419c3.jpg)
- The time trigger invokes the crawler function every five minutes. You can select the `Log` tab to search for the crawler execution log.

## Create a Serverless Service
The second [feature](https://www.alibabacloud.com/help/doc-detail/71229.htm) that we introduce allows anyone to send an HTTP request to trigger a function execution directly.

###Motivation
Now that we have a file system filled with the images downloaded from the web, we want to find a way to serve those images through a web service. The traditional way is to mount the NAS to a VM and start a webserver on it. This is both a waste of resources if the service is lightly used and not scalable when the traffic is heavy. Instead, you can write a serverless function that reads the images stored on the NAS file system and serve it through a HTTP endpoint. In this way, you can enjoy the instant scalability that Function Compute provides while still only pay for the actual usage.

###Image Processing Service Example
This demo shows how to write such a service. 

#### Create a Function with HTTP Trigger
1. Log on to the Function Compute console and select the same service as the crawler function.
2. Create a [function](https://www.alibabacloud.com/help/doc-detail/52077.htm) for the service by clicking the plus sign.
3. Select to create an empty python2.7 function again and click next.
4. Select HTTP trigger in the drop down menu and make sure that it supports both `GET` and `POST` invoke method and click next.
![HTTP Trigger.jpg](http://ata2-img.cn-hangzhou.img-pub.aliyun-inc.com/54a70c5ab70f0be46be31a6f91129115.jpg)
5. Finish the rest of the step and click OK.
6. Get the [files] (https://github.com/awesome-fc/using-nas/blob/master/python) from the same [github] (https://github.com/awesome-fc/using-nas) repro and upload the directory to the function.
![Upload Dir.jpg](http://ata2-img.cn-hangzhou.img-pub.aliyun-inc.com/3f6338a051d23f970ea3e93f9efec379.jpg)

#### Write the Function
Function Compute's [python](https://www.alibabacloud.com/help/doc-detail/56316.htm) runtime comes with many built-in modules that one can use. In this example, we use both [opencv](https://opencv-python-tutroals.readthedocs.io/en/latest/py_tutorials/py_setup/py_intro/py_intro.html) and [wand](http://docs.wand-py.org/en/0.4.4/) to do image transformations. 

The handler code is surprisingly straightforward and here is a high-level breakdown.
- Get the HTTP path from the system `environ` variable which helps generate the full path of the image on the NAS file system.
- Get the HTTP query parameters and store them in a dictionary.
- Apply different image processing techniques based on the `action` query.
- Insert the transformed image onto the pre-build html file and return it

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

#### Test it out
Now we have the function and the HTTP trigger ready, we can try something like [face detection](https://1986114430573743.cn-hongkong.fc.aliyuncs.com/2016-08-15/proxy/NASDemo/NASImageConverter/tmz/img/01b6a87e57b6da3b072fc10b1e45f37a.jpg?action=facedetect). You can edit the python code on the Function Compute console directly and add many more different image transformations and play with it.


##Conclusions
- One can read this [blog] (https://www.alibabacloud.com/blog/serverless-computing-with-alibaba-cloud-function-compute_593960) to get a better idea what [Function Compute](https://www.alibabacloud.com/product/function-compute) can do.
- One can also read the official NAS [tutorial] (https://www.alibabacloud.com/help/doc-detail/90025.htm) and other Function Compute [documentations](https://www.alibabacloud.com/help/doc-detail/52895.htm)
- Please give us feedbacks or suggestions in our official Function Compute [forum](https://www.alibabacloud.com/forum/thread-47).
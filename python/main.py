import base64
import cv2
import logging
import random
from wand.image import Image

TEMPLATE = open('/code/index.html').read()
NASROOT = '/mnt/crawler'
face_cascade = cv2.CascadeClassifier('/usr/share/opencv/lbpcascades/lbpcascade_frontalface.xml')


def handler(environ, start_response):
    logger = logging.getLogger()
    context = environ['fc.context']
    path = environ.get('PATH_INFO', "/")
    fileName = NASROOT + path
    logger.info("Operate on " + fileName)

    try:
        query_string = environ['QUERY_STRING']
        logger.info(query_string)
    except (KeyError):
        query_string = " "

    queries = query_string.split('&')
    assert len(queries) >= 1
    logger.info("Got %d query paramers" % (len(queries)))
    query_dist = {}
    for query in queries:
        query_tuple = query.split('=')
        query_dist[query_tuple[0]] = query_tuple[1]

    action = query_dist['action']
    logger.info("action is " + action)

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

    elif (action == "flip"):
        with Image(filename=fileName) as fc_img:
            fc_img.flip()
            img_enc = base64.b64encode(fc_img.make_blob(format='png'))

    elif (action == "flop"):
        with Image(filename=fileName) as fc_img:
            fc_img.flop()
            img_enc = base64.b64encode(fc_img.make_blob(format='png'))

    else:
        # demo, mixed operation
        fc_obj = NASROOT + "/tmz/img/1cf06f6470ab0c7fabf15382866168c8.jpg"
        py_obj = NASROOT + "/tmz/img/01b6a87e57b6da3b072fc10b1e45f37a.jpg"
        with Image(filename=fc_obj) as fc_img:
            with Image(filename=py_obj) as py_img:
                img = Image()
                py_img.flop()
                img.blank(fc_img.width + py_img.width + 20, py_img.height)
                img.composite(image=fc_img, left=0, top=0)
                img.composite(image=py_img, left=fc_img.width + 20, top=0)
                img.rotate(random.randint(0, 360))
                img_enc = base64.b64encode(img.make_blob(format='png'))

    status = '200 OK'
    response_headers = [('Content-type', 'text/html')]
    start_response(status, response_headers)
    return [TEMPLATE.replace('{fc-py}', img_enc)]

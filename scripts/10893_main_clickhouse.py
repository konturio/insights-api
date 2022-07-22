import threading

import correlation_request_10893


def get_file(filename):
    with open(file=filename, mode="r") as source:
        return source.read()


threads = []
for i in range(1):
    x = threading.Thread(target=correlation_request_10893.make_request, args=(get_file("geom10893_%s.json" % i), i,))
    x.start()
    threads.insert(i, x)
for i in range(1):
    threads[i].join()

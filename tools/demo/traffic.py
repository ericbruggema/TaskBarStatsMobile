from lib import *
import sys
if sys.argv[1] == "start":
    adb("push", os.environ["TEMP"] + "/demo/traffic.sh", "/data/local/tmp/traffic.sh")
    subprocess.Popen([ADB, "shell", "nohup sh /data/local/tmp/traffic.sh > /dev/null 2>&1 &"])
else:
    sh("pkill -f traffic.sh; pkill nc")

#!/bin/bash

#############
## Config ###
#############

#semester the course is being taught(Fall13)
term=$1
#Course Number(COMP115)
crs=$2
#length of recording in seconds
#starts 1 min before; normal class dur. 50mins; 5 mins longer;
#this usually adds up to 3360 seconds
dur=$3

#locks the process so nothing else can kill it; may not be necessary
#find what r is..
lck="/var/lock/rmanic.lck"

while [ -f $lck ]; do
    echo "locked...waiting $dur"
    dur=$((dur -1))
    if [ $dur -le 0 ]; then
	echo "waited to long"
	exit 0
    fi
    sleep 1
done

touch /var/lock/manic.lck
touch $lck
#//end lock

# Sets environment variables when running from cron (might not be portable)
. /etc/environment

#location for the recordings to be stored
#record=/recordings/raw
record="/home/paol/recordings/raw"
mkdir -p $record

pth=$record/$term/$crs/`date +%m-%d-%Y--%H-%M-%S`
pthCam=$pth/video.ogv
#log="sudo tee $pth/main.log" maynotbenecesary
log="tee $pth/main.log"

#location of capture scripts; hw:microphone; number corresponds to mic and cam

cameraFile="/home/paol/paol-code/cameraSetup.txt"
line=$(grep Video $cameraFile)
vidNum=${line:0:1}
vidFlip=${line:2:1}
line=$(grep Audio $cameraFile)
audNum=${line:0:1}
vidCam="/home/paol/paol-code/scripts/capture/videoCapture /dev/video$vidNum hw:$audNum $vidFlip"
dataCam="/home/paol/paol-code/captureProcessCode/multiCap"

##################
## PRE RECORDING ##
###################

## create path
mkdir -p $pth
mkdir -p $pth/wboard
mkdir -p $pth/computer
cd $pth

####################
## Record ##########
####################
vgaCount=0 #counter for vga feeds
camCount=0 #counter for wboard feeds

$dataCam $pth/wboard/ $pth/computer/ $dur $cameraFile &> $pth/dataCam.log &
$vidCam $dur $pth/video.mp4 &> $pth/vidCam.log &
#if you want the video name to have start time
#tfe="$(date +%s)"
#$vidCam $dur $pth/video-$tfe.mp4 &> $pth/vidCam.log &

vidCamPID=$!

#record information
echo "[course]" >> $pth/INFO
echo "id: $crs" >> $pth/INFO
echo "term: $term" >> $pth/INFO
echo "" >> $pth/INFO
echo "[pres]" >> $pth/INFO
echo "start: `date +%Y,%m,%d,%k,%M,%S`" >> $pth/INFO
echo "duration: $dur" >>$pth/INFO
echo "source: $(hostname)" >> $pth/INFO
echo "timestamp: $(date +%s)" >> $pth/INFO

lsusb >> $pth/devices.log
echo "" >> $pth/devices.log
ls /dev | grep video >> $pth/devices.log

echo "waiting for processes to finish"
#wait $dataCamPID
wait $pids
wait $vidCamPID

echo "Finished" | $log

rm -rf /var/lock/manic.lck
rm -rf $lck

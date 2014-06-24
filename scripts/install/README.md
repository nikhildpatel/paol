#PAOL machine install guide
Many of these will likely quickly fall out of day so please look at referenced webpages and update if it occurs.

##Install Scripts
###Total script install
To install all (individual scripts listed below and other essentials) run:
```
./installAll.sh
```

###Individual install scripts
ffmpeg
```
./sudo apt-get install libgl1-mesa-dev
```

OpenCV and single file needed by qtCreator (script was made based on instructions found at https://code.google.com/p/qt-opencv-multithreaded/wiki/Documentation)
```
opencvInstall
```

##Other Drivers and Software
###VGA2USB BROKEN
Find the kernal being run
```
uname -a
```

Go to epiphan's site and download the correct driver
```
http://www.epiphan.com/downloads/linux/index.php?dir=deb/
```
Double click on the downloaded package.

###QTCreator (stolen shamlessly from https://code.google.com/p/qt-opencv-multithreaded/wiki/Documentation)
Download the latest version of QT from the website:
```
http://qt-project.org/downloads
```
Then install using
```
chmod +x path_to_file/setup_file
./path_to_file/setup_file
```
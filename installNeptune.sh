#!/bin/bash
# This script configures the server for Nep-Tune

####################Setting variables##############################################################
installingUser=$(who am i | awk '{print $1}')  #The user who is executing this script should also be the user running Nep-Tune
packageManager=apt-get #Package manager, e.g. apt-get for Ubuntu, yum for CentOS / RedHat etc
tempDirectory=/tmp #Temp directory for NepTune is primarily used for live transcoded HLS files
thisDirectory=$(pwd)
yellow='\E[1;33m'
wipe="\033[1m\033[0m"

#Check user executed using "sudo"
executingUser=$(whoami)
if [ "$executingUser" != "root" ];
then
echo "Executing user is $executingUser"
echo "Script must be run with \"sudo\""
exit
fi

function getPassword {
read -s -p "Please enter the password for the samba share`echo $'\n> '`" passWord1
echo
read -s -p "Please re-enter the password`echo $'\n> '`" passWord2
echo
}

####################Reading in configuration via user prompt########################################
printf "$yellow";
read -p "Please enter the path to your samba share (e.g. smb://my.server.net/share/), including the / at the end`echo $'\n> '`" sambaServer
read -p "Please enter the folder containing your files on above share (e.g. ThisFolder/ThatFolder/), including the / at the end`echo $'\n> '`" sambaPath
read -p "Please enter the user name to access this share`echo $'\n> '`" sambaUser
getPassword

while [ "$passWord1" != "$passWord2" ]
do
echo Passwords do not match, please try again
getPassword
done
sambaPassword=${passWord1}
read -p "Would you like to initialize Nep-Tune after installation [Y/n]?`echo $'\n> '`" initializeAfterInstallation

printf "$yellow"; echo Fetching public key for MongoDB; printf "$wipe"
 sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv EA312927
 echo "deb http://repo.mongodb.org/apt/ubuntu xenial/mongodb-org/3.2 multiverse" | sudo tee /etc/apt/sources.list.d/mongodb-org-3.2.list
printf "$yellow";echo Updating;printf "$wipe";
 sudo $packageManager update -y
printf "$yellow";echo Installing dependencies; printf "$wipe";
 sudo $packageManager -y install autoconf automake build-essential libass-dev libfreetype6-dev libsdl1.2-dev libtheora-dev libtool libva-dev libvdpau-dev libvorbis-dev libxcb1-dev libxcb-shm0-dev libxcb-xfixes0-dev pkg-config texinfo zlib1g-dev mongodb-org default-jre default-jdk yasm libx264-dev gcc make
printf "$yellow";echo Opening firewall port 7701; printf "$wipe";
 sudo ufw allow 7701/tcp
 mkdir ~/ffmpeg_sources
printf "$yellow";echo Compiling YASM; printf "$wipe";
 cd ~/ffmpeg_sources
 wget http://www.tortall.net/projects/yasm/releases/yasm-1.3.0.tar.gz
 tar xzvf yasm-1.3.0.tar.gz
 cd yasm-1.3.0
 sudo ./configure --prefix="$HOME/ffmpeg_build" --bindir="$HOME/bin"
 sudo make
 sudo make install
 sudo make distclean
printf "$yellow";echo Complling AAC encoder; printf "$wipe";
 cd ~/ffmpeg_sources
 wget -O fdk-aac.tar.gz https://github.com/mstorsjo/fdk-aac/tarball/master
 tar xzvf fdk-aac.tar.gz
 cd mstorsjo-fdk-aac*
 sudo autoreconf -fiv
 sudo ./configure --prefix="$HOME/ffmpeg_build" --disable-shared
 sudo make
 sudo make install
 sudo make distclean
printf "$yellow";echo Compiling FFmpeg; printf "$wipe";
 cd ~/ffmpeg_sources
 wget http://ffmpeg.org/releases/ffmpeg-snapshot.tar.bz2
 tar xjvf ffmpeg-snapshot.tar.bz2
 cd ffmpeg
 PATH="$HOME/bin:$PATH"
 PKG_CONFIG_PATH="$HOME/ffmpeg_build/lib/pkgconfig"
 sudo ./configure --prefix="$HOME/ffmpeg_build" --pkg-config-flags="--static" --extra-cflags="-I$HOME/ffmpeg_build/include" --extra-ldflags="-L$HOME/ffmpeg_build/lib" --bindir="$HOME/bin" --enable-gpl --enable-libass --enable-libfdk-aac --enable-libfreetype --enable-libx264 --enable-nonfree
 sudo PATH="$HOME/bin:$PATH"
 sudo make
 sudo make install
 sudo make distclean
 sudo hash -r
printf "$yellow";echo Writing MongoDB config for systemd; printf "$wipe";

sudo cat > /lib/systemd/system/mongod.service << EOF
[Unit]
Description=High-performance, schema-free document-oriented database
After=network.target
Documentation=https://docs.mongodb.org/manual

[Service]
User=mongodb
Group=mongodb
ExecStart=/usr/bin/mongod --quiet --config /etc/mongod.conf

[Install]
WantedBy=multi-user.target
EOF

 sudo systemctl daemon-reload
printf "$yellow";echo Starting and enabling MongoDB; printf "$wipe";
 sudo service mongod start
 sudo systemctl enable mongod.service
ffmpeg=$(sudo runuser -l $installingUser -c 'which ffmpeg')

########################################Config File##############################################
printf "$yellow";echo Writing configuration file to /etc/nep-tune.properties; printf "$wipe";
sudo cat > /etc/nep-tune.properties << EOF
# For supported file types, see: https://tika.apache.org/1.13/formats.html

# Set credentials for samba share, taking care to get the / in the right place e.g.:
# sambaServer=smb://192.168.1.11/
# sambaUser=joe
# sambaPassword=passwordOfJoe
# sambaPath=Music/HardStuff/EvenHarder/
# Please note that entries are Case Sensitive

sambaServer=$sambaServer
sambaUser=$sambaUser
sambaPassword=$sambaPassword
sambaPath=$sambaPath

# Set logLevel one of debug, info, warning, error, critical. Default is warning. Debug logging is expensive!
logLevel=warning

# HTTP Server properties
httpServerPort=7701

# Specify temp directory used for creating HLS stream file (/tmp/) by default
# tempDirectory=$tempDirectory

# Path to ffmpeg executable, specified during installation using 'which ffmpeg'
ffmpegPath=$ffmpeg

# Maintenance interval in minutes.
maintenanceInterval=15

# Playlist delay in ms. For HLS, the first .ts file needs to be available for the client to retrieve
# before the playlist is sent (the playlist is written as stream files are created).
# The code recognises when the first entry in the playlist is present, but it does not know when
# the first stream file is available. 750ms after transcoding was started seems a good compromise.
# Decrease this value to reduce the time between selecting a stream and starting playback.
# Increase this value if nothing plays back after selecting a stream for playback.
waitBeforeSendingPlaylist=750
EOF

################################Service######################################
printf "$yellow";echo Writing service to /lib/systemd/system/nep-tune.service; printf "$wipe";
sudo cat > /lib/systemd/system/nep-tune.service << EOF
[Unit]
Description=Nep-tune indexing service
DefaultDependencies=no
Before=networking.service

[Service]
Type=forking
RemainAfterExit=no
ExecStart=${thisDirectory}/src/NepTune/runNeptune --daemon
ExecStop=pid="\${ps aux | grep NepTune | awk '{print \$2}'}"
ExecStop=kill \${pid}

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload

################################Run script######################################
printf "$yellow";echo Writing run script to ${thisDirectory}/src/NepTune/run.sh; printf "$wipe";
sudo cat > ${thisDirectory}/src/NepTune/runNeptune.sh << EOF
#!/bin/bash
#
echo Compiling
javac -g -Xlint:unchecked  -Xlint:deprecation -classpath .:${thisDirectory}/dist/lib/* ${thisDirectory}/src/NepTune/*.java
echo Finished compiling

#java -Xdebug -Xrunjdwp:transport=dt_socket,address=8800,server=y,suspend=y -cp ${thisDirectory}/src:${thisDirectory}/dist/lib/* NepTune.NepTune \$1 \$2 \$3
java -cp ${thisDirectory}/src:${thisDirectory}/dist/lib/* NepTune.NepTune \$1 \$2 \$3 >> /var/log/nep-tune.log 2>&1 &
EOF

sudo chmod +x ${thisDirectory}/src/NepTune/runNeptune.sh
sudo systemctl enable nep-tune.service # auto-start nep-tune

if [[ $initializeAfterInstallation =~ ^[Yy]$ ]]
then
${thisDirectory}/src/NepTune/run.sh --init
fi

printf "$yellow";
read -p "Installation has completed. Do you want to run the Nep-Tune daemon now (y/n)?`echo $'\n> '`" runDaemon
if [[ $runDaemon =~ ^[Yy]$ ]]
then
service nep-tune start;
fi
echo "Type \"less +F /var/log/nep-tune.log\" to check the logfile"
echo Script complete
printf "$wipe";
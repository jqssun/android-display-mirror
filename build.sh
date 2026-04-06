#!/bin/bash
mkdir -p .tmp app/src/main/assets app/src/main/jniLibs/{arm64-v8a,armeabi-v7a}; cd .tmp
wget "https://www.synaptics.com/sites/default/files/exe_files/2024-12/DisplayLink%C2%AE%20USB%20Graphics%20Software%20for%20Android%204.2.0-EXE.apk"
unzip -jo *.apk 'assets/*-dock-release.spkg' -d ../app/src/main/assets/
unzip -jo *.apk 'assets/*-monitor-release.spkg' -d ../app/src/main/assets/
unzip -jo *.apk 'lib/armeabi-v7a/libusb_android.so' -d ../app/src/main/jniLibs/armeabi-v7a/
unzip -jo *.apk 'lib/armeabi-v7a/libDisplayLinkManager.so' -d ../app/src/main/jniLibs/armeabi-v7a/
unzip -jo *.apk 'lib/armeabi-v7a/libAndroidDLM.so' -d ../app/src/main/jniLibs/armeabi-v7a/
unzip -jo *.apk 'lib/arm64-v8a/libusb_android.so' -d ../app/src/main/jniLibs/arm64-v8a/
unzip -jo *.apk 'lib/arm64-v8a/libDisplayLinkManager.so' -d ../app/src/main/jniLibs/arm64-v8a/
unzip -jo *.apk 'lib/arm64-v8a/libAndroidDLM.so' -d ../app/src/main/jniLibs/arm64-v8a/
cd ..; rm -rf .tmp
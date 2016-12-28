It's easy to use. There are 4 steps.

Step 1. Just copy modified QtMultimedia.jar and QtMultimedia-bundled.jar files into your C:\Qt\5.7\android_armv7\jar and/or C:\Qt\5.7\android_x86\jar directory. If you did this before go to next step.

Step 2. Copy libvlcjni.so file into your project directory.

Step 3. Add these lines to your .pro file

```
QT += multimedia

contains(ANDROID_TARGET_ARCH,armeabi-v7a) {
    ANDROID_EXTRA_LIBS = \
$$PWD/libvlcjni.so
}
```
Step 4. You must call encodeUrlForAndroid function. Otherwise you use default Android MediaPlayer class. So, QML file seems like this:
```
import QtQuick 2.7
import QtQuick.Controls 1.4
import QtQuick.Window 2.2
import QtQuick.Dialogs 1.2
import QtMultimedia 5.6

ApplicationWindow {
    title: qsTr("Hello World")
    width: 640
    height: 480
    visible: true
	
    function encodeUrlForAndroid(Source)
    {
        if(Qt.platform.os !== "android")
            return Source;

        // replace http to rtsp:
        if(Source.substring(0,7) === "http://")
        {
            Source = Source.substring(7);
            Source = "rtsp://" + Source + "???replaceRtspToHttp";
        }

        // add vlc:
        Source = Source + "???vlc";

        return Source;
    }	

    MediaPlayer{
        id: vlcMediaPlayer
        source: encodeUrlForAndroid("rtsp://wowzaec2demo.streamlock.net/vod/mp4:BigBuckBunny_115k.mov")
        autoPlay: true
    }
    VideoOutput{
        source: vlcMediaPlayer
        anchors.fill: parent
        fillMode: VideoOutput.Stretch
    }
}
```

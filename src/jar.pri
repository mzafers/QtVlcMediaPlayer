load(qt_build_paths)
CONFIG += java
DESTDIR = $$MODULE_BASE_OUTDIR/jar

JAVACLASSPATH += $$PWD/src

JAVASOURCES += $$PWD/src/org/qtproject/qt5/android/multimedia/QtAndroidMediaPlayer.java \
               $$PWD/src/org/qtproject/qt5/android/multimedia/QtCameraListener.java \
               $$PWD/src/org/qtproject/qt5/android/multimedia/QtSurfaceTextureListener.java \
               $$PWD/src/org/qtproject/qt5/android/multimedia/QtSurfaceTextureHolder.java \
               $$PWD/src/org/qtproject/qt5/android/multimedia/QtMultimediaUtils.java \
               $$PWD/src/org/qtproject/qt5/android/multimedia/QtMediaRecorderListener.java \
               $$PWD/src/org/qtproject/qt5/android/multimedia/QtSurfaceHolderCallback.java \
$$PWD/src/org/qtproject/qt5/android/multimedia/AWindow.java \
$$PWD/src/org/qtproject/qt5/android/multimedia/IAWindowNativeHandler.java \
$$PWD/src/org/qtproject/qt5/android/multimedia/IVLCVout.java \
$$PWD/src/org/qtproject/qt5/android/multimedia/LibVLC.java \
$$PWD/src/org/qtproject/qt5/android/multimedia/MainThread.java \
$$PWD/src/org/qtproject/qt5/android/multimedia/Media.java \
$$PWD/src/org/qtproject/qt5/android/multimedia/MediaDiscoverer.java \
$$PWD/src/org/qtproject/qt5/android/multimedia/MediaList.java \
$$PWD/src/org/qtproject/qt5/android/multimedia/MediaPlayer.java \
$$PWD/src/org/qtproject/qt5/android/multimedia/VLCEvent.java \
$$PWD/src/org/qtproject/qt5/android/multimedia/VLCObject.java \
$$PWD/src/org/qtproject/qt5/android/multimedia/media/MediaPlayer.java \
$$PWD/src/org/qtproject/qt5/android/multimedia/media/VideoView.java \
$$PWD/src/org/qtproject/qt5/android/multimedia/util/AndroidUtil.java \
$$PWD/src/org/qtproject/qt5/android/multimedia/util/Extensions.java \
$$PWD/src/org/qtproject/qt5/android/multimedia/util/HWDecoderUtil.java \
$$PWD/src/org/qtproject/qt5/android/multimedia/util/MediaBrowser.java \
$$PWD/src/org/qtproject/qt5/android/multimedia/util/VLCUtil.java			   

# install
target.path = $$[QT_INSTALL_PREFIX]/jar
INSTALLS += target

OTHER_FILES += $$JAVASOURCES

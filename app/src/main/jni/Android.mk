LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := nextv_gst_player
# Compile the same JNI implementation used by CMake source verification.
# Keeping two divergent implementations caused inconsistent lifecycle behaviour.
LOCAL_SRC_FILES := ../cpp/gstreamer_player_jni.cpp
LOCAL_SHARED_LIBRARIES := gstreamer_android
LOCAL_LDLIBS := -llog -landroid
include $(BUILD_SHARED_LIBRARY)

ifndef GSTREAMER_ROOT_ANDROID
$(error GSTREAMER_ROOT_ANDROID is required for native GStreamer playback)
endif

ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
GSTREAMER_ROOT := $(GSTREAMER_ROOT_ANDROID)/arm64
else ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
GSTREAMER_ROOT := $(GSTREAMER_ROOT_ANDROID)/armv7
else ifeq ($(TARGET_ARCH_ABI),x86_64)
GSTREAMER_ROOT := $(GSTREAMER_ROOT_ANDROID)/x86_64
else ifeq ($(TARGET_ARCH_ABI),x86)
GSTREAMER_ROOT := $(GSTREAMER_ROOT_ANDROID)/x86
else
$(error Unsupported GStreamer Android ABI: $(TARGET_ARCH_ABI))
endif

GSTREAMER_NDK_BUILD_PATH := $(GSTREAMER_ROOT)/share/gst-android/ndk-build
include $(GSTREAMER_NDK_BUILD_PATH)/plugins.mk
# Explicitly ship the video/audio sinks and format plugins used by live IPTV.
# glimagesink requires the OpenGL plugin, while Android MediaCodec decoding
# requires androidmedia. Include common transport and parser modules for
# MPEG-TS, HLS, DASH, RTSP, MP4, and Matroska without bundling every codec.
GSTREAMER_PLUGINS := \
    coreelements playback typefindfunctions autodetect app \
    videoconvert videoscale videoconvertscale videorate audioconvert audioresample volume \
    opengl androidmedia opensles \
    tcp udp soup rtsp rtp rtpmanager hls dash isomp4 matroska mpegtsdemux \
    videoparsersbad audioparsers
G_IO_MODULES := openssl
GSTREAMER_EXTRA_DEPS := gstreamer-video-1.0
include $(GSTREAMER_NDK_BUILD_PATH)/gstreamer-1.0.mk

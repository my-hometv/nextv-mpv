LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := nextv_gst_player
LOCAL_SRC_FILES := gstreamer_player.cpp
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
GSTREAMER_PLUGINS := coreelements playback typefindfunctions autodetect app videoconvertscale videorate videoscale videoconvert audioconvert audioresample volume tcp udp soup hls dash isomp4 mpegtsdemux tsdemux
G_IO_MODULES := openssl
GSTREAMER_EXTRA_DEPS := gstreamer-video-1.0
include $(GSTREAMER_NDK_BUILD_PATH)/gstreamer-1.0.mk

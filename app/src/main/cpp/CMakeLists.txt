cmake_minimum_required(VERSION 3.22.1)
project(nextv_gstreamer_probe LANGUAGES CXX)

# Legacy VLC probe is retained until all VLC call sites have been removed.
add_library(nextv_vlc_probe SHARED native_runtime_probe.cpp)
target_compile_features(nextv_vlc_probe PRIVATE cxx_std_17)
target_compile_options(nextv_vlc_probe PRIVATE -Wall -Wextra -Werror)
target_link_libraries(nextv_vlc_probe PRIVATE ${CMAKE_DL_LIBS})

# This is a GStreamer runtime presence probe, not the player JNI bridge.
add_library(nextv_gstreamer_probe SHARED gstreamer_runtime_probe.cpp)
target_compile_features(nextv_gstreamer_probe PRIVATE cxx_std_17)
target_compile_options(nextv_gstreamer_probe PRIVATE -Wall -Wextra -Werror)
target_link_libraries(nextv_gstreamer_probe PRIVATE ${CMAKE_DL_LIBS})


# Opt-in native player. Set NEXTV_GSTREAMER_SDK to a prepared official
# Android GStreamer SDK sysroot containing include/ and lib/<ABI>/.
# The target is deliberately absent without SDK headers/libraries; verification
# must fail closed instead of producing a misleading "GStreamer-ready" APK.
# Prefer an explicit CMake -D value, then the CI/environment setting.
# A missing SDK leaves the optional JNI player out of the normal build.
if(NOT DEFINED NEXTV_GSTREAMER_SDK AND DEFINED ENV{NEXTV_GSTREAMER_SDK})
    set(NEXTV_GSTREAMER_SDK "$ENV{NEXTV_GSTREAMER_SDK}")
endif()
set(NEXTV_GSTREAMER_SDK "${NEXTV_GSTREAMER_SDK}" CACHE PATH "Prepared GStreamer Android SDK sysroot")
if(NEXTV_GSTREAMER_SDK)
    set(GST_INCLUDE "${NEXTV_GSTREAMER_SDK}/include")
    set(GST_LIB "${NEXTV_GSTREAMER_SDK}/lib/${ANDROID_ABI}")
    if(NOT EXISTS "${GST_INCLUDE}/gstreamer-1.0/gst/gst.h")
        message(FATAL_ERROR "Missing official GStreamer headers: ${GST_INCLUDE}")
    endif()
    foreach(GST_DEP gstreamer-1.0 gstvideo-1.0 gobject-2.0 glib-2.0)
        if(NOT EXISTS "${GST_LIB}/lib${GST_DEP}.so")
            message(FATAL_ERROR "Missing GStreamer dependency lib${GST_DEP}.so for ${ANDROID_ABI}")
        endif()
    endforeach()
    if(NOT EXISTS "${GST_LIB}/glib-2.0/include/glibconfig.h")
        message(FATAL_ERROR "Missing ABI-specific glibconfig.h in ${GST_LIB}")
    endif()
    if(NOT EXISTS "${GST_INCLUDE}/gstreamer-1.0/gst/video/videooverlay.h")
        message(FATAL_ERROR "Missing GStreamer videooverlay.h")
    endif()
    add_library(nextv_gst_player SHARED gstreamer_player_jni.cpp)
    target_compile_features(nextv_gst_player PRIVATE cxx_std_17)
    target_include_directories(nextv_gst_player PRIVATE
        "${GST_INCLUDE}/gstreamer-1.0"
        "${GST_INCLUDE}/glib-2.0"
        "${GST_LIB}/glib-2.0/include")
    find_library(ANDROID_LIB android REQUIRED)
    target_link_libraries(nextv_gst_player PRIVATE
        "${GST_LIB}/libgstreamer-1.0.so"
        "${GST_LIB}/libgstvideo-1.0.so"
        "${GST_LIB}/libgobject-2.0.so"
        "${GST_LIB}/libglib-2.0.so"
        ${ANDROID_LIB})
endif()

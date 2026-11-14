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

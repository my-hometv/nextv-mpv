cmake_minimum_required(VERSION 3.22.1)
project(nextv_vlc_probe LANGUAGES CXX)

add_library(nextv_vlc_probe SHARED native_runtime_probe.cpp)
target_compile_features(nextv_vlc_probe PRIVATE cxx_std_17)
target_compile_options(nextv_vlc_probe PRIVATE -Wall -Wextra -Werror)
target_link_libraries(nextv_vlc_probe PRIVATE ${CMAKE_DL_LIBS})

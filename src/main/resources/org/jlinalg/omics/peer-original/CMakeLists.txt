cmake_minimum_required(VERSION 3.16)
project(OmicsMaticPeerObserver LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
if(NOT IS_DIRECTORY "${PEER_SOURCE}")
  message(FATAL_ERROR "Set PEER_SOURCE to the exact pinned PMBio source tree")
endif()
set(ALG "${PEER_SOURCE}/External/alglib/src")
add_library(peer_alglib STATIC
  "${ALG}/ap.cpp" "${ALG}/fasttransforms.cpp" "${ALG}/solvers.cpp"
  "${ALG}/dataanalysis.cpp" "${ALG}/linalg.cpp" "${ALG}/alglibmisc.cpp"
  "${ALG}/integration.cpp" "${ALG}/specialfunctions.cpp" "${ALG}/diffequations.cpp"
  "${ALG}/optimization.cpp" "${ALG}/interpolation.cpp" "${ALG}/statistics.cpp"
  "${ALG}/alglibinternal.cpp")
add_library(peer_dense_original STATIC
  "${PEER_SOURCE}/src/bayesnet.cpp" "${PEER_SOURCE}/src/array_helper.cpp"
  "${PEER_SOURCE}/src/vbfa.cpp")
target_include_directories(peer_dense_original PUBLIC "${PEER_SOURCE}/include"
  "${PEER_SOURCE}/External/Eigen-3.1.0-alpha2" "${ALG}")
add_executable(peer_capture peer_capture.cpp)
target_link_libraries(peer_capture PRIVATE peer_dense_original peer_alglib)
if(CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
  foreach(target peer_alglib peer_dense_original peer_capture)
    target_compile_options(${target} PRIVATE -fno-fast-math -ffp-contract=off)
  endforeach()
endif()

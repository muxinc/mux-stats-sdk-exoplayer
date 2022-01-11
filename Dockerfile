# Based on https://github.com/ainoya/docker-android-project
# then also from https://github.com/bitrise-io/android/blob/master/Dockerfile
FROM openjdk:11

ENV DEBIAN_FRONTEND noninteractive

ENV ANDROID_SDK_ROOT /usr/local/android-sdk-linux
# Preserved for backwards compatibility
ENV ANDROID_HOME /usr/local/android-sdk-linux

# Install dependencies
RUN dpkg --add-architecture i386 && \
    apt-get update && \
    apt-get install -yq libstdc++6:i386 zlib1g:i386 libncurses5:i386 wget:i386 unzip:i386 git --no-install-recommends

# Download and untar SDK
RUN cd /usr/local && \
    wget https://dl.google.com/android/repository/commandlinetools-linux-6858069_latest.zip -O android-commandline-tools.zip && \
    mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    unzip -q android-commandline-tools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools && \
    rm android-commandline-tools.zip && \
    mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/tools

ENV PATH ${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/cmdline-tools/tools/bin:${ANDROID_SDK_ROOT}/platform-tools:$PATH

# Install Android SDK components
RUN yes | sdkmanager --licenses
RUN sdkmanager "build-tools;29.0.3" "platform-tools" "extras;android;m2repository" "extras;google;m2repository" "platforms;android-29"

# Support Gradle
ENV TERM dumb
ENV JAVA_OPTS -Xms256m -Xmx512m

# Configure the Android SDK and ack the license agreement
RUN echo "sdk.dir=$ANDROID_HOME" > local.properties

# Run build task by default
CMD ./gradlew --info clean build

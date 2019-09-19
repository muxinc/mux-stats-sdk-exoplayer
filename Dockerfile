# Based on https://github.com/ainoya/docker-android-project
FROM openjdk:8

ENV DEBIAN_FRONTEND noninteractive

# Install dependencies
RUN dpkg --add-architecture i386 && \
    apt-get update && \
    apt-get install -yq libstdc++6:i386 zlib1g:i386 libncurses5:i386 --no-install-recommends

# Download and untar SDK
RUN wget https://dl.google.com/android/repository/sdk-tools-linux-3859397.zip
RUN unzip sdk-tools-linux-3859397.zip -d /usr/local/android-sdk-linux
ENV ANDROID_HOME /usr/local/android-sdk-linux
ENV ANDROID_SDK /usr/local/android-sdk-linux
ENV PATH ${ANDROID_HOME}/tools:${ANDROID_HOME}/tools/bin:$ANDROID_HOME/platform-tools:$PATH

# Install Android SDK components
RUN yes | sdkmanager --licenses
RUN sdkmanager "build-tools;27.0.3" "platform-tools" "extras;android;m2repository" "extras;google;m2repository" "platforms;android-23"

# Support Gradle
ENV TERM dumb
ENV JAVA_OPTS -Xms256m -Xmx512m

# Add data into image so we can later pull an initial set of dependencies
RUN mkdir /data
COPY . /data
WORKDIR /data

# Configure the Android SDK and ack the license agreement
RUN echo "sdk.dir=$ANDROID_HOME" > local.properties

# Pull all our dependencies into the image
RUN ./gradlew --info androidDependencies

# Run build task by default
CMD ./gradlew --info clean build

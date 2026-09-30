FROM java:8

# author infomation
MAINTAINER bootstack

# create bootstack application dir
RUN mkdir /bootstack

# set volume to /bootstack
VOLUME /bootstack

ADD bootstack-core.jar /bootstack/bootstack-core.jar

RUN sh -c 'touch /bootstack-core.jar'

ENV JAVA_OPTS=""

ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /bootstack/bootstack-core.jar" ]

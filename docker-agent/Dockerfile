FROM fedora:22

MAINTAINER David Lutterkort <lutter@watzmann.net>

RUN dnf -y update && \
    dnf -y install ruby rubygem-bundler iproute bind-utils iputils less jq \
                   procps-ng
RUN dnf -y install \
      http://yum.puppetlabs.com/puppetlabs-release-pc1-fedora-21.noarch.rpm && \
    dnf -y install puppet-agent && \
    dnf clean all

ADD puppet.sh /etc/profile.d/puppet.sh
ADD agent /usr/local/bin/agent
ADD bashrc /root/.bashrc

WORKDIR /srv/puppet
RUN mkdir -p /srv/puppet /var/lib/puppet /etc/puppet

# Make ping usable, http://www.projectatomic.io/blog/2015/04/problems-with-ping-in-containers-on-atomic-hosts/
RUN setcap cap_net_raw,cap_net_admin+p /usr/bin/ping

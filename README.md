# Puppet development tools

One of the problems when developing Puppet codes is having a light-weight
way to run agents in full, including making any changes that the agent
might have to make to the system with simple ways to reset the agent state
and retry agent runs. This repository contains a setup to run a Puppet
agent from source inside a Docker container.

## General setup

Before you can build and run an agent inside a Docker container, you need
to have the following

1. A checkout of the [Puppet source](https://github.com/puppetlabs/puppet)
1. Set the environment variable `PUPPET_DIR` to the absolute path of that
source checkout
1. Read through the [Puppet development quickstart](https://github.com/puppetlabs/puppet/blob/master/docs/quickstart.md)
1. Make bundler install gems inside the Puppet source directory by running
   `bundle install --path .bundle/gems/` inside it.

This works using a Fedora 22 Docker host for both building and running the
container; I have no idea if it will work on other OS's/distros.

## Building the container image

The `Dockerfile` in `docker-agent/Dockerfile` describes the container; to
build it, you need to

1. Pull a Fedora 22 base image with `docker pull fedora:22`
1. Run `./docker-agent/build`

Note that the container does not have an init system in it, and therefore
running services is not possible.

## Running the container image

To launch the container run `./docker-agent/launch <AGENT_NAME>`; the
`AGENT_NAME` should be the unqualified name of the agent, for example `db1`
or `web1`. The launch script will add the domain of the current host
automatically.

The general setup assumes that you are running the Puppet master on the
Docker host, and makes the agent resolve `puppet` to the IP address that
the host has on the `docker0` bridge.

The launch script drops you into a shell inside the container. You can run
the agent simple using the command `agent`, which will run it with some
sensible default options, or you can run `agent OPTS` which will run
`puppet agent OPTS` from your source checkout.

The launch script sets things up so that state that is important for Puppet
is stored on the host, and will therefore survive restarts of the
container. Anything else inside the container is ephemeral and will be
discarded when the container exits.

The following directories are mapped from the host:

* `/srv/puppet`: your Puppet source checkout in `PUPPET_DIR`
* `/etc/puppet`: maps to `state/agents/AGENT_NAME/etc` in the checkout of
                 this git repo; the `state` directory is used to preserve
                 important state across container runs.
* `/var/lib/puppet` : maps to `state/agents/AGENT_NAME/var`

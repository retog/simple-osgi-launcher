# Simple OSGi Launcher [![Build Status](https://travis-ci.org/retog/simple-osgi-launcher.svg?branch=master)](https://travis-ci.org/retog/simple-osgi-launcher)

I'm sick of complicated launchers. I just want to run my OSGi projects, without
configuration and deployment.

This project provides a maven plugin that allows to create executable jar files 
from OSGi bundle maven projects. Simply specify the required bundles as 
runtime dependencies to your project.

The goal is not to create a launcher-builder for all possible setting, but a
simplest possible tool to crate executable launchers for maven projects.

This project uses some code and libraries of [bnd](https://github.com/bndtools/bnd), 
but doesn't use any of the dependency resolution mechanism, bnd(run)-files and 
repositories, it simply creates a launcher out of the artifacts specified (as 
dependencies) in the pom.xml.

## Building

You might have guessed:

    mvn install

## Usage

From the folder where the pom of your OSGi project is, run:

    mvn clean org.wymiwyg.simple-osgi-launcher:osgi-launcher-maven-plugin:1.0.0-SNAPSHOT:create-launcher

Or better, add the following to your pom.xml:

            <plugin>
                <groupId>org.wymiwyg.simple-osgi-launcher</groupId>
                <artifactId>osgi-launcher-maven-plugin</artifactId>
                <version>1.0.0-SNAPSHOT</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>create-launcher</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>

As a result you will have a file ending with `launcher.jar` in your target directory.

Execute it with:

    java -jar myproject-launcher.jar

See the [example project](example/) for an example.


## Alternatives

There are several more powerful alternatives to create executable launchers.


### [The Sling Launchpad](https://sling.apache.org/documentation/the-sling-engine/the-sling-launchpad.html)

Very versatile and configurable launcher builder.

Disadvantage:

 * You typically need several projects, at least one with the bundle, one with the launcher, and one with the partial bundle list
 * They invented yet another format to describe the configuration, I don't learning new formats, especially if there is no autocompletion in my IDE

###[bnd OSGi launcher](http://bnd.bndtools.org/chapters/300-launching.html)

This code bases on the bnd-launcher.

What I don't like so much

 * It doesn't support maven
 * Also, yet another file format
 * It doesn't seem to be possible to just specify maven artifacts in the bndrun file
 * The dependency resolution mechanism can be quite slow and I only got it to work with a hard t manage local repo
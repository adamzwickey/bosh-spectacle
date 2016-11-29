=============
BOSH Spectacle
=============

A read-only view in BOSH Director

<kbd>![alt-text](https://github.com/azwickey-pivotal/bosh-spectacle/blob/master/screenshot.png)</kbd>

### Deploying

* Clean and build the project with Maven
```
$ mvn clean build
```

* Update the following 3 properties in your cloudfoundry manifest.yml file
```
  env:
    bosh.director.clientId: xxxx
    bosh.director.ip: xxxx
    bosh.director.secret: xxxx
```

* Push the application to cloudfoundry
```
$ cf push
```

Instructions to build
-------------------------------
The dependency sas-wiremock-extension must be manually added to maven repository, instructions below.

- clone domain-proxy-sat-tests
  https://gerrit-gamma.gic.ericsson.se/#/admin/projects/OSS/com.ericsson.oss.testware/domain-proxy-sat-tests
- Build sas-wiremock-extension inside domain-proxy-sat-tests and add it to local repository
  # from domain-proxy-sat-tests root folder
   ```cd sas-wiremock-extension/```
   ``` mvn clean install ```
  # from your user folder (ex : /c/Users/SIGNUM)
```mkdir .m2/repository/com/ericsson/oss/services/domainproxy/sas-wiremock-extension/0.0.1/ ```


    
```cp .m2/repository/com/ericsson/nms/sas-wiremock-extension/0.0.1/sas-wiremock-extension-0.0.1.jar  .m2/repository/com/ericsson/oss/services/domainproxy/sas-wiremock-extension/0.0.1/ ```

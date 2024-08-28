## Private Network Test Server
The Private Network Test Server allows for testing the Domain Coordinator Manager service with multiple private networks at scale.

It can be used to mock the REST responses of the Domain Coordinators that would be running in multiple private networks.

### Setting up the Private Network Test Server on a physical deployment or vApp
*Note: if jdk1.8.0_212 is already installed on the Netsim VM by the dp-testserver, skip steps 2 & 3
1. Login to a Netsim VM attached to the deployment
2. In the /netsim/ folder, download Java using the following command:
```bash
wget https://arm1s11-eiffel004.eiffel.gic.ericsson.se:8443/nexus/service/local/repo_groups/public/content/com/oracle/server-jre/1.8.0_212/server-jre-1.8.0_212-x64.tar.gz
```
3. Extract Java inside the /netsim/ folder:
```bash
tar -zxvf server-jre-1.8.0_212-x64.tar.gz
```
4. Create a new directory called ```pn_test```
5. Copy over the following files from this repo to the ```pn_test``` directory:
    1. ```target/pn-testserver-jar-with-dependencies.jar```
    2. ```deploy/log4j.xml```
    3. ```deploy/pn-config.yml```
    4. ```deploy/ipsearch.pl```
6. Identify an available range of IP addresses to use for the mock private networks
   1. Run the ipsearch.pl script
   ```bash
   ./ipsearch.pl
   ```
   2. The resultant output contains a list of available IP addresses on the Netsim VM. Take note of an available continuous range of 300 addresses that can be used, for example "192.168.100.1-192.168.101.44"
7. Edit the pn-config.yml configuration file for the mock PN test server. This file will contain the identified range of IP addresses to be used by the mock PN test server as a property.
   Example pn-config.yml file contents:
```bash
ipRange: "192.168.100.1-192.168.101.44"
verboseLogging: false
```
8. Start the Private Network Test Server, providing the pn-config.yml file:
```bash
nohup bash -c "/netsim/jdk1.8.0_212/bin/java -Xms1024m -Xmx2048m -XX:+UseG1GC -jar pn-testserver-jar-with-dependencies.jar pn-config.yml &"
```
9. Check the ```nohup.out``` log to ensure that the Private Network Test Server has successfully started.

## Running the Domain Coordinator Manager scale test plan using JMeter
The Domain Coordinator Manager scale test plan can be used to perform scalability testing of the Domain Coordinator Manager Service component against
multiple private networks.
It can utilise the Private Network Test Server set up in the previous steps above.

JMeter provides a quick and easy automated way to run the scale test cases, and provide us with some meaningful results.
The tests can also be re-run again at any time.
These tests should be set up and run on the deployment itself, rather than from your local machine using the JMeter GUI.
Running the tests from the deployment is faster and has little network latency, giving more accurate results.

### Setting up JMeter on a physical deployment or vApp
1. Login to a Scripting VM on the deployment
2. Create a new directory called ```jmeter```, and change into this directory
3. Download JMeter into the ```jmeter``` directory using the following command:
```bash
wget https://ftp.heanet.ie/mirrors/www.apache.org/dist//jmeter/binaries/apache-jmeter-5.4.1.zip
```
4. Unzip JMeter into the current directory:
```bash
unzip apache-jmeter-5.4.1.zip && rm -rf apache-jmeter-5.4.1.zip
```
### Running the JMeter test plan on a physical deployment or vApp
1. Copy over the following files from this repo to the ```jmeter``` directory on the deployment:
   1. ```jmeter/scale_test_setup.py```
   2. Depending on deployment type (physical or vApp), either:
       1. ```jmeter/dcm_scale_testing_423.jmx```
       2. ```jmeter/dcm_scale_testing_vapp.jmx```
2. Run the following setup command, specifying the same IP address range used for the private network test server,
   and the number of baseband node FDNs to generate, e.g.:
```bash
python3 scale_test_setup.py "192.168.100.1-192.168.101.44" 2000
```
3. Check that the following files have been generated. These files are to be read by the DCM Scale Test Plan:
   1. ```baseband_mappings.csv```
   2. ```group_ids.csv```
4. Start the scale test plan by executing the following command from the ```jmeter``` directory:
```bash
nohup bash -c "apache-jmeter-5.4.1/bin/jmeter.sh -n -t dcm_scale_testing.jmx &"
```
5. Check the ```nohup.out``` log file to ensure that JMeter is executing the test plan.
6. When the test run is finished, check that the following test output files have been created in the ```results``` directory:
```bash
overall_results_summary.jtl
tc1_set_dc_to_baseband_mappings.jtl
tc2_show_mappings_for_dc_single.jtl
tc3_show_mappings_for_dc_multi.jtl
tc4_show_dc_for_baseband_single.jtl
tc5_show_dc_for_baseband_multi.jtl
tc6_get_group_grants.jtl
tc7_get_all_groups.jtl
tc8_remove_all_groups.jtl
tc9_remove_dc_to_baseband_mappings.jtl
```

### Examining the results
The result files from the previous step can be examined on your own local machine using Excel.

1. Copy the contents of the ```results``` directory from the previous step to your local machine
2. Open Excel
3. Open one of the above .jtl files. Ensure that Comma is selected as a delimiter when importing
4. Ensure that the data is successfully imported
5. You can then use the 'elasped' column data to create a graph of the request response times (Y-axis is milliseconds, X-axis is the request)
6. Examine the DDP site for the deployment, and check the following:
   1. CPU, garbage collection, heap usage, etc. for domainproxy VM
   2. Postgres data for dcmdb, e.g. rows inserted, rows, returned, rows deleted etc.
7. Observe domainproxy VM server log size before and after, and log rotation
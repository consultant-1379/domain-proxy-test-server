#!/bin/bash

# to be run on the scripting vm

cat <<EOT >> batch_execute.py

import enmscripting as enm
import fileinput

session = enm.open()
cmd = session.terminal()

for line in fileinput.input():
    print('Executing: %s' % line)
    result = cmd.execute(line.rstrip())
    print('\n'.join(result.get_output()))

print('THE END')

enm.close(session)

EOT

echo "-> waiting for testserver to be up"
curl  --head -X GET --retry 50 --retry-connrefused --retry-delay 5 http://192.168.0.2:9919/__admin/cbrs/report

echo "-> adding nodes"
curl -fsSL http://192.168.0.2:9919/__admin/cbrs/add-node-commands | python batch_execute.py

echo "-> adding cpi data"
curl -fsSL http://192.168.0.2:9919/__admin/cbrs/cpi > cpi.txt
echo "yes" | cbrscpi delete all
echo "cpi.txt" | cbrscpi import


echo "-> setting SAS url"
cbrs config --sas-url 'http://192.168.0.2:9919/'

echo "-> waiting for nodes to sync"
slpTic=0
while [ $slpTic -le 200 ]; do

    status=$(echo 'cmedit get * cmfunction.syncstatus' | python batch_execute.py |grep syncStatus|grep -v ': SYNCHRONIZED'|grep Status|wc -l)

    if [ $status -gt  0 ]; then
        ((slpTic++))
        echo "Sleep ${slpTic}(x10)   pending: ${status}"
    else
        echo "All nodes are synced"
        break
    fi
    sleep 10
done

echo "-> Available groups to add:"
curl -fsSL http://192.168.0.2:9919/__admin/cbrs/group/ids  > groupids.txt
cat groupids.txt

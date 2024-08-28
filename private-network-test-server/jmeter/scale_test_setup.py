#!/usr/bin/python3

import csv
import ipaddress
import math
import sys

MAPPINGS_OUTPUT_FILE = "baseband_mappings.csv"
GROUPS_OUTPUT_FILE = "group_ids.csv"


def generate_ip_addresses(ip_address_range):
    ip_address_range_ips = ip_address_range.split('-')
    start_ip = ipaddress.IPv4Address(ip_address_range_ips[0])
    end_ip = ipaddress.IPv4Address(ip_address_range_ips[1])

    ip_addresses = []
    for ip_int in range(int(start_ip), int(end_ip) + 1):
        ip_addresses.append(str(ipaddress.IPv4Address(ip_int)))
    return ip_addresses


def create_dc_instance_pairs(ip_address_list):
    return [";".join(dc_instance) for dc_instance in batch(ip_address_list, 2)]


def generate_baseband_fdns(number_of_basebands):
    baseband_fdns = []
    for num in range(1, number_of_basebands + 1):
        node_num = str(num).zfill(5)
        baseband_fdns.append(
            "SubNetwork=ONRM_ROOT_MO,SubNetwork=BB_G2,MeContext=LTE40dg2ERBS{0},ManagedElement=LTE40dg2ERBS{0}"
            .format(node_num))
    return baseband_fdns


def map_basebands_to_dc_instances(dc_instances, baseband_fdns):
    batch_size = math.ceil(len(baseband_fdns) / len(dc_instances))
    baseband_fdns_batch = [baseband_fdn for baseband_fdn in batch(baseband_fdns, batch_size)]

    basebands_to_dc_instances = {}
    for index, dc_instance in enumerate(dc_instances):
        if index < len(baseband_fdns_batch):
            basebands_to_dc_instances[dc_instance] = baseband_fdns_batch[index]
    return basebands_to_dc_instances


def create_groups(number_of_basebands):
    group_ids = []
    for num in range(1, number_of_basebands + 1):
        node_num = str(num).zfill(5)
        group_ids.append(
            "ONRM_ROOT_MO|BB_G2|LTE40dg2ERBS{0}:LTE40dg2ERBS{0}:LTE40dg2ERBS{0}-1,LTE40dg2ERBS{0}-2,LTE40dg2ERBS{0}-3"
            .format(node_num))
        group_ids.append(
            "ONRM_ROOT_MO|BB_G2|LTE40dg2ERBS{0}:LTE40dg2ERBS{0}:LTE40dg2ERBS{0}-4,LTE40dg2ERBS{0}-5,LTE40dg2ERBS{0}-6,LTE40dg2ERBS{0}-7"
            .format(node_num))
    return group_ids


def batch(iterable, n=1):
    length = len(iterable)
    for index in range(0, length, n):
        yield iterable[index: min(index + n, length)]


def write_baseband_mappings_to_file(baseband_mappings):
    try:
        with open(MAPPINGS_OUTPUT_FILE, 'w') as mappings_file:
            writer = csv.writer(mappings_file)
            count = 0
            for dc_id, basebands in baseband_mappings.items():
                for baseband in basebands:
                    writer.writerow([dc_id, baseband])
                    count += 1
            print("{0} baseband mappings written to file {1}".format(count, MAPPINGS_OUTPUT_FILE))
    except IOError as ioe:
        print("Error writing baseband mappings to file", ioe)


def write_group_ids_to_file(group_ids):
    try:
        with open(GROUPS_OUTPUT_FILE, 'w') as groups_file:
            writer = csv.writer(groups_file)
            count = 0
            for group_id in group_ids:
                writer.writerow([group_id])
                count += 1
            print("{0} group ids written to file {1}".format(count, GROUPS_OUTPUT_FILE))
    except IOError as ioe:
        print("Error writing group ids to file", ioe)


def main(args):
    if len(args) < 2:
        print("Error: Provide IP address range, and number of baseband nodes")
        return
    ip_address_range = args[0]
    number_of_basebands = int(args[1])

    ip_address_list = generate_ip_addresses(ip_address_range)
    dc_instances = create_dc_instance_pairs(ip_address_list)
    baseband_fdns = generate_baseband_fdns(number_of_basebands)

    baseband_mappings = map_basebands_to_dc_instances(dc_instances, baseband_fdns)
    write_baseband_mappings_to_file(baseband_mappings)

    group_ids = create_groups(number_of_basebands)
    write_group_ids_to_file(group_ids)


if __name__ == '__main__':
    main(sys.argv[1:])

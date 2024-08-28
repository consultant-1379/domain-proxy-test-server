#!/usr/bin/env perl
#
# author: enembal
#

use strict;

my $IFCONF = qx(/sbin/ifconfig -a);

my @IP_RAW = split (/\n/, $IFCONF);

@IP_RAW = grep(/inet/, @IP_RAW);

my @IP_LIST;

foreach my $IP_LINE (@IP_RAW){
	my @FIELDS1 = split(' ', $IP_LINE);
	push(@IP_LIST, $FIELDS1[1]);
}

@IP_LIST = grep(!/127.0.0.1/, @IP_LIST);
my @SORTEDIPS = sort ipcomp @IP_LIST;

my $lastIP = "";
my %IPgap;
foreach my $IP (@SORTEDIPS){
	if($lastIP ne "" && nextIP($lastIP) ne $IP){
		$IPgap{$lastIP}=$IP;
	}
	$lastIP = $IP;
}

foreach my $gapbegin (keys(%IPgap)){
	print "There is a gap in allocated IPs between $gapbegin and $IPgap{$gapbegin}\n";
}

my $IPUsedRaw = qx(netstat -l);
my @IPUsedLines = split(/\n/, $IPUsedRaw);
@IPUsedLines = grep(/LISTEN\s+/, @IPUsedLines);
@IPUsedLines = grep(/([0-9]{1,3}\.){3}[0-9]{1,3}/, @IPUsedLines);
my %UsedIPs;
foreach my $line (@IPUsedLines){
	my @fields = split(/\s+/, $line);
	@fields = grep(/([0-9]{1,3}\.){3}[0-9]{1,3}/, @fields);
	my @IP = split(/:/, $fields[0]);
	$UsedIPs{$IP[0]} = 1;
}

foreach my $IP (@SORTEDIPS){
	if( ! $UsedIPs{$IP}){
		print "$IP is allocated but not used.\n";
	}
}


###############
# SUBROUTINES #
###############

sub printlist(@){
	foreach (@_){
		print "$_\n";
	}
}

sub ipcomp($$){
	my $IPA = shift;
	my $IPB = shift;
	my @IP_A = split(/\./, $IPA);
        my @IP_B = split(/\./, $IPB);
	if(($IP_A[0] <=> $IP_B[0]) != 0){
		return $IP_A[0] <=> $IP_B[0];
	}elsif (($IP_A[1] <=> $IP_B[1]) != 0){
		return $IP_A[1] <=> $IP_B[1];
	}elsif (($IP_A[2] <=> $IP_B[2]) != 0){
                return $IP_A[2] <=> $IP_B[2];
        }else{
                return $IP_A[3] <=> $IP_B[3];
        }
}

sub nextIP($){
	my $IP = shift;
	my @IPArr = split(/\./, $IP);
	if($IPArr[3] < 255){
		$IPArr[3]++;
	}elsif($IPArr[2] < 255){
                $IPArr[2]++;
		$IPArr[3]=0;
        }elsif($IPArr[1] < 255){
                $IPArr[1]++;
                $IPArr[3]=0;
                $IPArr[2]=0;
	}else {
		$IPArr[0]++;
		$IPArr[3]=0;
		$IPArr[2]=0;
		$IPArr[1]=0;
}
	return "$IPArr[0].$IPArr[1].$IPArr[2].$IPArr[3]";
}



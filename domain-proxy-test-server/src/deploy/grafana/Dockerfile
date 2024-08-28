FROM grafana/grafana:6.1.6

ADD prometheus.yml /etc/grafana/provisioning/datasources/prometheus.yml
ADD dashboard_file_provider.yml /etc/grafana/provisioning/dashboards/dashboard_file_provider.yml

RUN mkdir -p /var/tmp/provisioned/dashboards

ADD DomainProxyTestServer.json /var/tmp/provisioned/dashboards/DomainProxyTestServer.json

VOLUME /var/lib/grafana
EXPOSE 3000
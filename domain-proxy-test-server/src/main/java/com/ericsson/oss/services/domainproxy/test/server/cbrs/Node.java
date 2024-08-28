package com.ericsson.oss.services.domainproxy.test.server.cbrs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.tomakehurst.wiremock.common.Json;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(exclude = "cells")
public class Node implements NodeManagement {
    @JsonView(Json.PublicView.class)
    private final String name;
    @JsonView(Json.PublicView.class)
    private final String address;
    @JsonView(Json.PublicView.class)
    private final int port;
    @JsonView(Json.PublicView.class)
    private final Set<Cell> cells = new HashSet<>();
    @JsonIgnore
    private NodeManagement management;
    @JsonView(Json.PublicView.class)
    private State state = State.STOPPED;
    @JsonView(Json.PublicView.class)
    private String remarks;
    @JsonView(Json.PublicView.class)
    private long latencyMillis;

    @Override
    public void start() {
        management.start();
    }

    @Override
    public void stop() {
        management.stop();
    }

    public enum State {
        STOPPED, STARTED;
    }

    @JsonIgnore
    public boolean isManageable() {
        return this.management != null;
    }

    @JsonIgnore
    public boolean isStarted() {
        return State.STARTED.equals(state);
    }

    public List<String> getGroupIds() {
        final List<Group> groups = new ArrayList<>(cells.size());
        this.cells.stream().filter(Cell::isCbrsEnabled).forEach( cell -> cell.getCbsds().forEach(cbsd -> {
            final String cellId = cell.getCellId();
            final String cbsdSerial = cbsd.getCbsdSeria();
            final Optional<Group> existingGroup = groups.stream().filter(group -> group.hasCbsdSerial(cbsdSerial) || group.hasCellId(cellId)).findAny();
            if (existingGroup.isPresent()) {
                existingGroup.get().addCbsdAndCell(cbsdSerial, cellId);
            } else {
                groups.add(new Group(cbsdSerial, cellId));
            }
        }));

        return groups.stream().map(g -> g.generateGroupId(this.name)).collect(Collectors.toList());
    }

    private static class Group {
        private final Set<String> cbsdSerials = new HashSet<>();
        private final Set<String> cellIds = new HashSet<>();

        public Group(final String cbsdSerial, final String cellId) {
            this.cbsdSerials.add(cbsdSerial);
            this.cellIds.add(cellId);
        }

        public boolean hasCellId(final String cellId) {
            return this.cellIds.contains(cellId);
        }

        public boolean hasCbsdSerial(final String cbsdSerial) {
            return this.cbsdSerials.contains(cbsdSerial);
        }

        public void addCbsdAndCell(final String cbsdSerial, final String cellId) {
            this.cbsdSerials.add(cbsdSerial);
            this.cellIds.add(cellId);
        }

        public String generateGroupId(final String nodeName) {
            final String cellIdsPart = cellIds.stream().sorted().map(id -> {
                final String[] parts = id.split("=");
                return parts[parts.length - 1];
            }).collect(Collectors.joining(","));

            return nodeName + ":" + cellIdsPart;
        }
    }
}

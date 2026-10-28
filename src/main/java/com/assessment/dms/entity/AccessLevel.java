package com.assessment.dms.entity;


public enum AccessLevel {
    VIEW,

    DOWNLOAD;

    public boolean allows(AccessLevel required) {
        return this == DOWNLOAD || required == VIEW;
    }
}
